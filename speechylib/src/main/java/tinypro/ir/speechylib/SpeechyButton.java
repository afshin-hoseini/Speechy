package tinypro.ir.speechylib;

import android.Manifest;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.Rect;
import android.graphics.Region;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.ShapeDrawable;
import android.graphics.drawable.shapes.OvalShape;
import android.os.Build;
import android.os.Bundle;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.support.v4.app.ActivityCompat;
import android.util.AttributeSet;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageSwitcher;
import android.widget.ImageView;
import android.widget.ViewSwitcher;

import org.jetbrains.annotations.Nullable;
import org.w3c.dom.Attr;

import java.io.Console;
import java.text.AttributedCharacterIterator;
import java.util.ArrayList;
import java.util.Timer;
import java.util.TimerTask;

/**
 * Created by afshinhoseini on 2/24/18.
 */

public class SpeechyButton extends FrameLayout {

    public enum Status {

        Ready, Preparing, Listening, Error
    }

    private float rmsValue = 0;
    private Paint soundVisualizerPaint = null;
    private SpeechRecognizer speechRecognizer = null;
    private String locale = "en";

    private Status status = Status.Ready;
    private ImageSwitcher imgIcon = null;
    private Timer errorDismissTimer = null;
    private View content = null;
    private Rect clipRect = null;
    private int iconColor = 0xffffffff;
    private int soundVisualizationColor = 0x000000;
    private Integer lastError = null;
    private Runnable speechFinalizer = null;
    private Callbacks callbackListener = null;
    private ArrayList<String> supportedLocales = new ArrayList<>();
    private final float maxRmsValue = 10;
    private final float rmsZoom = 5;

// ____________________________________________________________________

    public SpeechyButton(Context context) {
        super(context);
        init(null);
    }

    public SpeechyButton(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(attrs);
    }

    public SpeechyButton(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(attrs);
    }

// ____________________________________________________________________

    public void disableClipOnParents(View v) {
        if (v.getParent() == null) {
            return;
        }

        if (v instanceof ViewGroup) {
            ((ViewGroup) v).setClipChildren(false);
        }

        if (v.getParent() instanceof View) {
            disableClipOnParents((View) v.getParent());
        }
    }

// ____________________________________________________________________

    private void init(AttributeSet attrs) {

        Log.e("SPEECHY", "----> INIT  <-----");

        if(attrs != null) {

            TypedArray a = getContext().obtainStyledAttributes (attrs,R.styleable.SpeechyButton);
            try {

                this.soundVisualizationColor = a.getColor(R.styleable.SpeechyButton_iconTintColor, 0xff000000);
                this.iconColor = a.getColor(R.styleable.SpeechyButton_soundVisualizationColor, 0xffffffff);
            } finally {
                a.recycle();
            }
        }

        content = LayoutInflater.from(getContext()).inflate(R.layout.speechy_button_view, this, false);
        addView(content);


        //Initializes the icon image switcher view.
        imgIcon = content.findViewById(R.id.img_icon);
        imgIcon.setAnimateFirstView(false);
        imgIcon.setInAnimation(getContext(), R.anim.in_icon_anim);
        imgIcon.setOutAnimation(getContext(), R.anim.out_icon_anim);

        if(Build.VERSION.SDK_INT >= 21)
            imgIcon.setElevation(getElevation());

        Drawable backGround = getBackground();
        if (backGround != null) {

            if (backGround instanceof ColorDrawable) {

                OvalShape ovalShape = new OvalShape();
                ShapeDrawable shapeBkg = new ShapeDrawable(ovalShape);
                shapeBkg.setColorFilter(((ColorDrawable) backGround).getColor(), PorterDuff.Mode.SRC_ATOP);
                imgIcon.setBackground(shapeBkg);
            }
            else {

                imgIcon.setBackground(backGround);
            }
        }


        disableClipOnParents(imgIcon);

        imgIcon.setFactory(new ViewSwitcher.ViewFactory() {
            @Override
            public View makeView() {
                ImageView imageView = new ImageView(getContext());
                imageView.setScaleType(ImageView.ScaleType.CENTER);
                imageView.setLayoutParams(
                        new ImageSwitcher.LayoutParams(
                                LayoutParams.MATCH_PARENT,
                                LayoutParams.WRAP_CONTENT));
                ((LayoutParams)imageView.getLayoutParams()).gravity = Gravity.CENTER;
                imageView.setColorFilter(iconColor, PorterDuff.Mode.SRC_ATOP);
                return imageView;
            }
        });


        //Inits the sound visualization circle's paint.
        soundVisualizerPaint = new Paint();
        soundVisualizerPaint.setColor(soundVisualizationColor);
        soundVisualizerPaint.setStyle(Paint.Style.FILL);

//        setOnClickListener(onClick);
        setBackgroundColor(0);


        setOnTouchListener(new OnTouchListener() {
            @Override
            public boolean onTouch(View view, MotionEvent event) {



                if(event.getAction() == MotionEvent.ACTION_DOWN) {

                    Log.e("RECO_S", "OnTouch START  ------------>");
                    start();
                }
                else if (event.getAction() == MotionEvent.ACTION_UP) {

                    Log.e("RECO_S", "OnTouch STOP  ------------X");
                    stop();
                }

                return true;
            }
        });

        setClipChildren(false);
        setClipToPadding(false);
        loadSupportedLocales();
    }


// ____________________________________________________________________

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();

        setStatus(this.status);
    }

// ____________________________________________________________________

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {

        super.onSizeChanged(w, h, oldw, oldh);

        int padding = (int) (maxRmsValue * rmsZoom);
        clipRect = new Rect(0 - padding , 0 - padding, w + padding, h + padding);
    }

// ____________________________________________________________________

    @Override
    public void setEnabled(boolean enabled) {

        super.setEnabled(enabled);
        setGreyscale(!enabled);
    }

// ____________________________________________________________________

    private void setGreyscale(boolean greyscale) {
        if (greyscale) {
            // Create a paint object with 0 saturation (black and white)
            ColorMatrix cm = new ColorMatrix();
            cm.setSaturation(0);
            Paint greyscalePaint = new Paint();
            greyscalePaint.setColorFilter(new ColorMatrixColorFilter(cm));
            // Create a hardware layer with the greyscale paint
            setLayerType(LAYER_TYPE_HARDWARE, greyscalePaint);
        } else {
            // Remove the hardware layer
            setLayerType(LAYER_TYPE_NONE, null);
        }
    }


// ____________________________________________________________________

    public void setCallbackListener(Callbacks listener) {

        if (listener == null) {

            listener = new Callbacks() {
                @Override
                public boolean onStart(SpeechyButton button) {
                    return false;
                }

                @Override
                public boolean onFinished(SpeechyButton button, @Nullable Error error) {
                    return false;
                }

                @Override
                public boolean result(SpeechyButton button, @Nullable String result, boolean isPartial) {
                    return false;
                }
            };
        }

        callbackListener = listener;
    }


// ____________________________________________________________________

    public void setLocale (String locale) {

        this.locale = locale;

        checkLocaleIsSupported();
    }

// ____________________________________________________________________

    private void setStatus(Status status) {

        switch (status) {

            case Preparing:

                imgIcon.setImageResource(R.drawable.ic_stop_white_24dp);
                break;
            case Error:

                imgIcon.setImageResource(R.drawable.ic_error_outline_white_24dp);

                if(errorDismissTimer != null)
                    errorDismissTimer.cancel();

                errorDismissTimer = new Timer();
                errorDismissTimer.schedule(new TimerTask() {
                    @Override
                    public void run() {

                        post(new Runnable() {
                            @Override
                            public void run() {
                                if (getStatus() == Status.Error)
                                    setStatus(Status.Ready);
                            }
                        });
                    }
                }, 3000);

                break;
            case Listening:

                break;
            case Ready:

                imgIcon.setImageResource(R.drawable.ic_mic_white_24dp);
                break;
        }
        this.status = status;
    }

// ____________________________________________________________________

    private Status getStatus() {

        return this.status;
    }

// ____________________________________________________________________

    private void loadSupportedLocales() {

        Intent intent = RecognizerIntent.getVoiceDetailsIntent(getContext());

        if(intent == null)
            return;

        LocalesReceiver localesReceiver = new LocalesReceiver();
        getContext().sendOrderedBroadcast(intent,
                null, localesReceiver, null,
                Activity.RESULT_OK, null, null);
    }

// ____________________________________________________________________

    public void stop() {

        if(speechRecognizer != null && (getStatus() == Status.Preparing || getStatus() == Status.Listening)) {

            speechRecognizer.stopListening();
            callbackListener.onFinished(this, null);
        }
    }

// ____________________________________________________________________

    public void start() {

        Log.e("RECO_S", "START");

        if (getStatus() == Status.Preparing || getStatus() == Status.Listening)
            return;

        Activity activity = (Activity) getContext();
        if (activity == null)
            return;


        if (ActivityCompat.checkSelfPermission(getContext(), Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {

            Intent recognizerIntent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);

            recognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, locale);
            recognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, locale);
            recognizerIntent.putExtra(RecognizerIntent.EXTRA_SUPPORTED_LANGUAGES, locale);
            recognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
            recognizerIntent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3);

            recognizerIntent.putExtra(RecognizerIntent.EXTRA_ONLY_RETURN_LANGUAGE_PREFERENCE, locale);
            recognizerIntent.putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, locale);
            recognizerIntent.putExtra(RecognizerIntent.EXTRA_RESULTS, locale);
            recognizerIntent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);

            setCallbackListener(this.callbackListener);
            speechFinalizer = null;
            lastError = null;
            callbackListener.onStart(this);

            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(getContext());
            speechRecognizer.setRecognitionListener(speechListener);
            speechRecognizer.startListening(recognizerIntent);

            setStatus(Status.Preparing);

        }
        else {
            ActivityCompat.requestPermissions(activity, new String[]{Manifest.permission.RECORD_AUDIO}, 100);
        }
    }

// ____________________________________________________________________


    @Override
    public void draw(Canvas canvas) {

        canvas.clipRect(clipRect);

        if(getStatus() == Status.Listening)
            canvas.drawCircle(getWidth()/2, getHeight()/2, getWidth()/2 + rmsValue, soundVisualizerPaint);

        super.draw(canvas);
    }

// ____________________________________________________________________

    private void notifyFinished(@Nullable Integer error) {

        if (error != null)
            this.lastError = error;

        if(speechFinalizer == null) {

            speechFinalizer = new Runnable() {
                @Override
                public void run() {

                    speechFinalizer = null;
                    setStatus(SpeechyButton.this.lastError != null ? Status.Error : Status.Ready);
                    speechRecognizer.destroy();

                    Callbacks.Error err = SpeechyButton.this.lastError != null ? Callbacks.Error.getError( SpeechyButton.this.lastError) : null;
                    callbackListener.onFinished(SpeechyButton.this, err);
                }
            };

            postDelayed(speechFinalizer, 400);
        }


    }

// ____________________________________________________________________

    private void checkLocaleIsSupported() {

        if ( supportedLocales == null || supportedLocales.size() == 0)
            return; //Assumes it's supported

        post(new Runnable() {
            @Override
            public void run() {

                setEnabled(supportedLocales.contains(locale));
            }
        });

    }

// ____________________________________________________________________

    private OnClickListener onClick = new OnClickListener() {
        @Override
        public void onClick(View view) {

            Log.e("RECO_S", "onClick");
            if (getStatus() == Status.Error || getStatus() == Status.Ready)
                start();
            else
                stop();
        }
    };

// ____________________________________________________________________

    private RecognitionListener speechListener = new RecognitionListener() {
        @Override
        public void onReadyForSpeech(Bundle bundle) {

            Log.e("REC", "ready");
        }

        @Override
        public void onBeginningOfSpeech() {

            Log.e("REC", "Begin");
            setStatus(Status.Listening);
        }

        @Override
        public void onRmsChanged(float v) {

            float newValue = v > 0 ? v * rmsZoom : 0;
            if (newValue != rmsValue) {

                rmsValue = newValue;
                invalidate(clipRect);
            }


            Log.e("REC", v + "");
        }

        @Override
        public void onBufferReceived(byte[] bytes) {

        }

        @Override
        public void onEndOfSpeech() {

            Log.e("REC", "End");
            notifyFinished(null);
        }

        @Override
        public void onError(int err) {

            Log.e("REC", "Error");
            notifyFinished(err);
        }

        @Override
        public void onResults(Bundle results) {

            Log.e("REC", "Result");

            ArrayList<String> matches = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
            String text = null;

            if(matches != null && matches.size() > 0)
                text = matches.get(0);

            callbackListener.result(SpeechyButton.this, text, false);
        }

        @Override
        public void onPartialResults(Bundle results) {

            Log.e("REC", "Partial result");

            ArrayList<String> matches = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
            String text = null;

            if(matches != null && matches.size() > 0)
                text = matches.get(0);

            if(text != null)
                callbackListener.result(SpeechyButton.this, text, true);
        }

        @Override
        public void onEvent(int i, Bundle bundle) {

            Log.e("REC", "Event");
        }
    };

// ____________________________________________________________________

    public interface  Callbacks {

        enum Error {

            Internet, Recognition, Audio, Permission;

            public static Error getError(int code) {

                switch (code) {
                    case SpeechRecognizer.ERROR_AUDIO: return Audio;
                    case SpeechRecognizer.ERROR_CLIENT: return Audio;
                    case SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS: return Permission;
                    case SpeechRecognizer.ERROR_NETWORK: return Internet;
                    case SpeechRecognizer.ERROR_NETWORK_TIMEOUT: return Internet;
                    case SpeechRecognizer.ERROR_NO_MATCH: return Recognition;
                    case SpeechRecognizer.ERROR_RECOGNIZER_BUSY: return Recognition;
                    case SpeechRecognizer.ERROR_SERVER: return Internet;
                    case SpeechRecognizer.ERROR_SPEECH_TIMEOUT: return Recognition;
                    default:
                        return Recognition;
                }
            }
        }

        boolean onStart(SpeechyButton button);
        boolean onFinished(SpeechyButton button, @Nullable Error error);
        boolean result(SpeechyButton button, @Nullable String result, boolean isPartial);
    }

// ____________________________________________________________________

    private class LocalesReceiver extends BroadcastReceiver {

        @Override
        public void onReceive(Context context, Intent intent)
        {

            Bundle extras = getResultExtras(true);
            supportedLocales = extras.getStringArrayList(RecognizerIntent.EXTRA_SUPPORTED_LANGUAGES);

            checkLocaleIsSupported();
        }
    }

// ____________________________________________________________________



}
