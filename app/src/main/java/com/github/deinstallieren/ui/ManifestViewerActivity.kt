package com.github.deinstallieren.ui

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.github.deinstallieren.databinding.ActivityManifestViewerBinding
import com.github.deinstallieren.utils.ManifestParser
import kotlinx.coroutines.launch

class ManifestViewerActivity : AppCompatActivity() {

    private lateinit var binding: ActivityManifestViewerBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityManifestViewerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val packageName = intent.getStringExtra("EXTRA_PACKAGE_NAME") ?: return finish()
        val appName = intent.getStringExtra("EXTRA_APP_NAME") ?: packageName
        val sourceDir = intent.getStringExtra("EXTRA_SOURCE_DIR") ?: return finish()

        binding.tvManifestTitle.text = "$appName ($packageName)"
        binding.tvManifestContent.text = "Cargando manifiesto..."

        lifecycleScope.launch {
            val manifestContent = ManifestParser.decodeManifest(sourceDir)
            binding.tvManifestContent.text = manifestContent
        }
    }
}
