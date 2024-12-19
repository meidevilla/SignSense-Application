package com.example.signsense

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity

class HomeActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_home)

        with(findViewById<Button>(R.id.buttonMainActivity)) {
            setOnClickListener { navigateTo(LiveInference::class.java) }
        }

        with(findViewById<Button>(R.id.buttonImageInference)) {
            setOnClickListener { navigateTo(ImageInference::class.java) }
        }

        with(findViewById<Button>(R.id.buttonAbout)) {
            setOnClickListener { navigateTo(AboutActivity::class.java) }
        }
    }

    private fun navigateTo(destination: Class<*>) {
        startActivity(Intent(this, destination))
    }
}
