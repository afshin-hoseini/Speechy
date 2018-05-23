package tinypro.ir.speechy

import android.support.v7.app.AppCompatActivity
import android.os.Bundle
import android.view.View
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.android.synthetic.main.activity_main.view.*
import tinypro.ir.speechylib.SpeechyButton

class MainActivity : AppCompatActivity() ,  SpeechyButton.Callbacks{


    override fun onStart(button: SpeechyButton?): Boolean {

        txtResult.setText("...")
        return true
    }

    override fun onFinished(button: SpeechyButton?, error: SpeechyButton.Callbacks.Error?): Boolean {

        when(error) {

            SpeechyButton.Callbacks.Error.Audio -> txtResult.setText("!! AUDIO !!")
            SpeechyButton.Callbacks.Error.Internet -> txtResult.setText("!! NO INTERNET !!")
            SpeechyButton.Callbacks.Error.Permission -> txtResult.setText("!! NO PERMISSION !!")
            SpeechyButton.Callbacks.Error.Recognition -> txtResult.setText("!! RECOGNITION !!")
        }
        return true
    }

    override fun result(button: SpeechyButton?, result: String?, isPartial: Boolean): Boolean {

        var result = result
        if (result != null && isPartial)
            result += "..."
        txtResult.text = result

        return true
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        speechy.setLocale("fa-IR")
        speechy.setCallbackListener(this);
    }

}
