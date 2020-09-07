package com.dragon.playapng

import android.graphics.drawable.Drawable
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity
import com.dragon.apnglib.APngDrawable
import com.dragon.apnglib.playAPngAsset
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel

class MainActivity : AppCompatActivity(), CoroutineScope by MainScope() {
    lateinit var imageView: ImageView
    lateinit var imageView1: ImageView
    lateinit var imageView2: ImageView
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        imageView = findViewById(R.id.imageView)
        imageView1 = findViewById(R.id.image1)
        imageView2 = findViewById(R.id.image2)
        try {
            imageView1.setImageDrawable(Drawable.createFromStream(assets.open("google.png"), null))
            imageView2.setImageDrawable(Drawable.createFromStream(assets.open("blued.png"), null))
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        cancel()
    }

    fun onClickView(view: View) {
        when (view.id) {
            R.id.image1 -> {
                imageView.playAPngAsset(this, "google.png")
            }
            R.id.image2 -> {
                imageView.playAPngAsset(this, "blued.png")
            }
            R.id.imageView -> (imageView.drawable as? APngDrawable)?.let {
                if (it.isRunning) {
                    it.stop()
                } else {
                    it.start()
                }
            }
        }
    }
}