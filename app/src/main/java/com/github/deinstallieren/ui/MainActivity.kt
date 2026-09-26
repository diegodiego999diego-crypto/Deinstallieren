package com.github.deinstallieren.ui

import android.app.AlertDialog
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.PopupMenu
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.github.deinstallieren.R
import com.github.deinstallieren.adapter.AppAdapter
import com.github.deinstallieren.databinding.ActivityMainBinding
import com.github.deinstallieren.model.AppItem
import com.github.deinstallieren.utils.ApkManager
import com.github.deinstallieren.utils.ShizukuCommander
import rikka.shizuku.Shizuku
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var adapter: AppAdapter
    private var currentFilterType = 0 // 0: Todas, 1: Sistema, 2: Usuario

    private val shizukuPermissionListener = Shizuku.OnRequestPermissionResultListener { _, grantResult ->
        if (grantResult == PackageManager.PERMISSION_GRANTED) {
            binding.bannerShizuku.visibility = View.GONE
            loadInstalledApps()
        } else {
            binding.bannerShizuku.visibility = View.VISIBLE
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupRecyclerView()
        setupListeners()
        checkShizuku()
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            Shizuku.removeRequestPermissionResultListener(shizukuPermissionListener)
        } catch (e: Throwable) {}
    }

    private fun checkShizuku() {
        try {
            if (Shizuku.isPreV11() || !Shizuku.pingBinder()) {
                binding.bannerShizuku.visibility = View.VISIBLE
                return
            }
            if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
                binding.bannerShizuku.visibility = View.GONE
                loadInstalledApps()
            } else {
                Shizuku.addRequestPermissionResultListener(shizukuPermissionListener)
                Shizuku.requestPermission(0)
            }
        } catch (e: Throwable) {
            binding.bannerShizuku.visibility = View.VISIBLE
        }
    }

    private fun setupRecyclerView() {
        adapter = AppAdapter { item, anchorView ->
            showAppMenu(item, anchorView)
        }
        binding.rvApps.layoutManager = LinearLayoutManager(this)
        binding.rvApps.adapter = adapter
    }

    private fun setupListeners() {
        binding.btnTrash.setOnClickListener {
            startActivity(Intent(this, TrashActivity::class.java))
        }

        binding.rgFilter.setOnCheckedChangeListener { _, checkedId ->
            currentFilterType = when (checkedId) {
                R.id.rbSystem -> 1
                R.id.rbUser -> 2
                else -> 0
            }
            adapter.filter(binding.etSearch.text.toString(), currentFilterType)
        }

        binding.etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                adapter.filter(s?.toString() ?: "", currentFilterType)
            }
            override fun afterTextChanged(s: Editable?) {}
        })
    }

    private fun loadInstalledApps() {
        lifecycleScope.launch {
            val list = mutableListOf<AppItem>()
            val pm = packageManager

            withContext(Dispatchers.IO) {
                val installed = pm.getInstalledApplications(PackageManager.GET_META_DATA)
                for (info in installed) {
                    val label = pm.getApplicationLabel(info).toString().ifBlank { info.packageName }
                    val isSystem = (info.flags and ApplicationInfo.FLAG_SYSTEM) != 0
                    val isUpdated = (info.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0
                    val isChipset = AppItem.isChipsetPackage(info.packageName)
                    val icon = try { pm.getApplicationIcon(info) } catch (e: Throwable) { null }

                    list.add(
                        AppItem(
                            packageName = info.packageName,
                            appName = label,
                            icon = icon,
                            isSystem = isSystem,
                            isUpdatedSystem = isUpdated,
                            isChipset = isChipset,
                            isEnabled = info.enabled
                        )
                    )
                }
                list.sortBy { it.appName.lowercase() }
            }
            adapter.submitList(list)
        }
    }

    private fun showAppMenu(item: AppItem, anchor: View) {
        val popup = PopupMenu(this, anchor)

        if (item.isChipset) {
            popup.menu.add(getString(R.string.action_extract))
            popup.menu.add(getString(R.string.action_manifest))
        } else {
            popup.menu.add(getString(R.string.action_uninstall))
            popup.menu.add(getString(R.string.action_disable))
            popup.menu.add(getString(R.string.action_suspend))
            popup.menu.add(getString(R.string.action_extract))
            popup.menu.add(getString(R.string.action_manifest))
        }

        popup.setOnMenuItemClickListener { menuItem ->
            when (menuItem.title) {
                getString(R.string.action_uninstall) -> handleUninstall(item)
                getString(R.string.action_disable) -> handleDisable(item)
                getString(R.string.action_suspend) -> handleSuspend(item)
                getString(R.string.action_extract) -> handleExtract(item)
                getString(R.string.action_manifest) -> handleManifest(item)
                else -> false
            }
        }
        popup.show()
    }

    private fun handleUninstall(item: AppItem): Boolean {
        if (!item.isSystem || item.isUpdatedSystem) {
            AlertDialog.Builder(this)
                .setMessage(getString(R.string.warning_user_app))
                .setNegativeButton(getString(R.string.btn_cancel), null)
                .setNeutralButton(getString(R.string.btn_accept)) { _, _ ->
                    executeUninstallUser(item, backup = false)
                }
                .setPositiveButton(getString(R.string.btn_extract_and_uninstall)) { _, _ ->
                    executeUninstallUser(item, backup = true)
                }
                .show()
        } else {
            lifecycleScope.launch {
                val ok = ShizukuCommander.uninstallSystem(item.packageName)
                if (ok) {
                    Toast.makeText(this@MainActivity, "App desinstalada", Toast.LENGTH_SHORT).show()
                    adapter.removeItem(item.packageName)
                } else {
                    Toast.makeText(this@MainActivity, "Error", Toast.LENGTH_SHORT).show()
                }
            }
        }
        return true
    }

    private fun executeUninstallUser(item: AppItem, backup: Boolean) {
        lifecycleScope.launch {
            if (backup) {
                val appInfo = packageManager.getApplicationInfo(item.packageName, 0)
                ApkManager.backupAppInternally(this@MainActivity, appInfo)
            }
            val ok = ShizukuCommander.uninstallUser(item.packageName)
            if (ok) {
                Toast.makeText(this@MainActivity, "App desinstalada", Toast.LENGTH_SHORT).show()
                adapter.removeItem(item.packageName)
            } else {
                Toast.makeText(this@MainActivity, "Error", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun handleDisable(item: AppItem): Boolean {
        lifecycleScope.launch {
            val ok = ShizukuCommander.disable(item.packageName)
            if (ok) {
                Toast.makeText(this@MainActivity, "App inhabilitada", Toast.LENGTH_SHORT).show()
                item.isEnabled = false
                adapter.notifyDataSetChanged()
            } else {
                Toast.makeText(this@MainActivity, "Error", Toast.LENGTH_SHORT).show()
            }
        }
        return true
    }

    private fun handleSuspend(item: AppItem): Boolean {
        lifecycleScope.launch {
            val ok = ShizukuCommander.suspendApp(item.packageName)
            if (ok) {
                Toast.makeText(this@MainActivity, "App suspendida", Toast.LENGTH_SHORT).show()
                item.isSuspended = true
                adapter.notifyDataSetChanged()
            } else {
                Toast.makeText(this@MainActivity, "Error", Toast.LENGTH_SHORT).show()
            }
        }
        return true
    }

    private fun handleExtract(item: AppItem): Boolean {
        lifecycleScope.launch {
            val appInfo = packageManager.getApplicationInfo(item.packageName, 0)
            val ok = ApkManager.exportToDownloads(this@MainActivity, appInfo, item.appName)
            if (ok) {
                Toast.makeText(this@MainActivity, "APK extraído", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this@MainActivity, "Error", Toast.LENGTH_SHORT).show()
            }
        }
        return true
    }

    private fun handleManifest(item: AppItem): Boolean {
        try {
            val appInfo = packageManager.getApplicationInfo(item.packageName, 0)
            val intent = Intent(this, ManifestViewerActivity::class.java).apply {
                putExtra("EXTRA_PACKAGE_NAME", item.packageName)
                putExtra("EXTRA_APP_NAME", item.appName)
                putExtra("EXTRA_SOURCE_DIR", appInfo.sourceDir)
            }
            startActivity(intent)
        } catch (e: Throwable) {
            Toast.makeText(this, "Error", Toast.LENGTH_SHORT).show()
        }
        return true
    }
}
