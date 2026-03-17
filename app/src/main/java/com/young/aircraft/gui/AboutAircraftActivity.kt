package com.young.aircraft.gui

import android.content.Intent
import android.graphics.BitmapFactory
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.net.toUri
import androidx.lifecycle.lifecycleScope
import com.young.aircraft.BuildConfig
import com.young.aircraft.R
import com.young.aircraft.databinding.ActivityAboutAircraftBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.URL

class AboutAircraftActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAboutAircraftBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAboutAircraftBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnBack.setOnClickListener { finish() }

        setupBanner()
        setupDescription()
        setupGithubLink()
        loadProjectImage()
    }

    private fun setupBanner() {
        binding.tvVersionBadge.text = getString(R.string.device_info_fmt_version, BuildConfig.VERSION_NAME)
        binding.tvPlatformBadge.text = "Android ${android.os.Build.VERSION.RELEASE}"
    }

    private fun setupDescription() {
        binding.tvDescription.text = getString(R.string.about_description)
    }

    private fun setupGithubLink() {
        val githubUrl = "https://github.com/tobecrazy/Aircraft"
        binding.tvGithubUrl.text = githubUrl
        binding.llGithubLink.setOnClickListener {
            startActivity(Intent(Intent.ACTION_VIEW, githubUrl.toUri()))
        }
    }

    private fun loadProjectImage() {
        val imageUrl = "https://images.cnblogs.com/cnblogs_com/tobecrazy/432338/o_250810143405_Card.png"
        binding.progressImage.visibility = View.VISIBLE

        lifecycleScope.launch {
            val bitmap = withContext(Dispatchers.IO) {
                runCatching {
                    URL(imageUrl).openStream().use { BitmapFactory.decodeStream(it) }
                }.getOrNull()
            }
            binding.progressImage.visibility = View.GONE
            if (bitmap != null) {
                binding.ivProject.setImageBitmap(bitmap)
            }
        }
    }
}
