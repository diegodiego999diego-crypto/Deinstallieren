package com.github.deinstallieren.ui

import android.content.pm.PackageManager
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.view.ContextThemeWrapper
import androidx.appcompat.widget.PopupMenu
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.github.deinstallieren.R
import com.github.deinstallieren.adapter.AppAdapter
import com.github.deinstallieren.databinding.ActivityTrashBinding
import com.github.deinstallieren.model.AppItem
import com.github.deinstallieren.utils.ShizukuCommander
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class TrashActivity : AppCompatActivity() {

    private lateinit var binding: ActivityTrashBinding
    private lateinit var adapter: AppAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityTrashBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupRecyclerView()
        loadRecycledApps()
    }

    private fun setupRecyclerView() {
        adapter = AppAdapter { item, anchorView ->
            showTrashMenu(item, anchorView)
        }
        binding.rvTrash.layoutManager = LinearLayoutManager(this)
        binding.rvTrash.adapter = adapter
    }

    private fun loadRecycledApps() {
        lifecycleScope.launch {
            val list = mutableListOf<AppItem>()
            val pm = packageManager

            withContext(Dispatchers.IO) {
                // 1. Cargar apps inhabilitadas
                val (_, disabledOutput) = ShizukuCommander.exec("pm list packages -d")
                val disabledPackages = disabledOutput.lines()
                    .filter { it.startsWith("package:") }
                    .map { it.removePrefix("package:").trim() }

                for (pkg in disabledPackages) {
                    try {
                        val appInfo = pm.getApplicationInfo(pkg, PackageManager.MATCH_UNINSTALLED_PACKAGES)
                        val name = pm.getApplicationLabel(appInfo).toString().ifBlank { pkg }
                        val icon = try { pm.getApplicationIcon(appInfo) } catch (e: Throwable) { null }
                        list.add(AppItem(pkg, name, icon, isSystem = true, isUpdatedSystem = false, isChipset = false, isEnabled = false, isUninstalled = false))
                    } catch (e: Throwable) {}
                }

                // 2. Cargar apps desinstaladas de sistema (-u)
                val (_, uninstalledOutput) = ShizukuCommander.exec("pm list packages -u -s")
                val uninstalledPackages = uninstalledOutput.lines()
                    .filter { it.startsWith("package:") }
                    .map { it.removePrefix("package:").trim() }

                for (pkg in uninstalledPackages) {
                    if (list.any { it.packageName == pkg }) continue
                    try {
                        val appInfo = pm.getApplicationInfo(pkg, PackageManager.MATCH_UNINSTALLED_PACKAGES)
                        val name = pm.getApplicationLabel(appInfo).toString().ifBlank { pkg }
                        val icon = try { pm.getApplicationIcon(appInfo) } catch (e: Throwable) { null }
                        list.add(AppItem(pkg, name, icon, isSystem = true, isUpdatedSystem = false, isChipset = false, isEnabled = false, isUninstalled = true))
                    } catch (e: Throwable) {}
                }

                // 3. Cargar apps respaldadas localmente
                val backupRoot = File(filesDir, "backups")
                if (backupRoot.exists()) {
                    backupRoot.listFiles()?.forEach { dir ->
                        if (dir.isDirectory) {
                            val pkg = dir.name
                            if (list.none { it.packageName == pkg }) {
                                list.add(AppItem(pkg, pkg, null, isSystem = false, isUpdatedSystem = false, isChipset = false, isEnabled = false, isUninstalled = true))
                            }
                        }
                    }
                }
            }
            adapter.submitList(list)
        }
    }

    private fun showTrashMenu(item: AppItem, anchor: View) {
        val wrapper = ContextThemeWrapper(this, R.style.Theme_Deinstallieren)
        val popup = PopupMenu(wrapper, anchor)
        val hasLocalBackup = File(filesDir, "backups/${item.packageName}").exists()

        if (!item.isUninstalled) {
            // Solo está inhabilitada
            popup.menu.add(getString(R.string.action_enable))
        } else {
            // Está desinstalada
            popup.menu.add(getString(R.string.action_reinstall))
        }

        popup.setOnMenuItemClickListener { menuItem ->
            when (menuItem.title) {
                getString(R.string.action_enable) -> {
                    lifecycleScope.launch {
                        val ok = ShizukuCommander.enable(item.packageName)
                        if (ok) {
                            Toast.makeText(this@TrashActivity, "App habilitada", Toast.LENGTH_SHORT).show()
                            adapter.removeItem(item.packageName)
                        } else {
                            Toast.makeText(this@TrashActivity, "Error", Toast.LENGTH_SHORT).show()
                        }
                    }
                    true
                }
                getString(R.string.action_reinstall) -> {
                    lifecycleScope.launch {
                        val ok = if (hasLocalBackup) {
                            ShizukuCommander.reinstallFromBackupSession(this@TrashActivity, item.packageName)
                        } else {
                            ShizukuCommander.reinstallExisting(item.packageName)
                        }
                        if (ok) {
                            Toast.makeText(this@TrashActivity, "App reinstalada", Toast.LENGTH_SHORT).show()
                            adapter.removeItem(item.packageName)
                        } else {
                            Toast.makeText(this@TrashActivity, "Error", Toast.LENGTH_SHORT).show()
                        }
                    }
                    true
                }
                else -> false
            }
        }
        popup.show()
    }
}
