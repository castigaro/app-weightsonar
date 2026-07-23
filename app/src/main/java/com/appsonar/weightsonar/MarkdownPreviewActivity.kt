package com.appsonar.weightsonar

import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import com.appsonar.weightsonar.databinding.ActivityMarkdownPreviewBinding
import com.google.android.material.snackbar.Snackbar
import io.noties.markwon.Markwon
import io.noties.markwon.ext.tables.TablePlugin
import java.io.File
import java.io.IOException

/**
 * Gerenderte Vorschau der exportierten Markdown-Auswertung (Markwon);
 * Speichern über den System-Dialog (SAF) und Teilen per Share-Sheet —
 * ganz ohne separaten Viewer.
 */
class MarkdownPreviewActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMarkdownPreviewBinding
    private lateinit var file: File
    private lateinit var markdown: String

    // Legt die Datei über den System-Dialog an (SAF) — dafür braucht die
    // App keine Speicher-Berechtigung.
    private val createDocument = registerForActivityResult(
        ActivityResultContracts.CreateDocument("text/markdown")
    ) { uri ->
        if (uri == null) return@registerForActivityResult
        try {
            // "wt" = write + truncate, damit kürzerer Text keine Altreste hinterlässt.
            contentResolver.openOutputStream(uri, "wt")?.bufferedWriter()?.use {
                it.write(markdown)
            } ?: throw IOException("Stream ist null")
            Snackbar.make(binding.root, R.string.saved, Snackbar.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Snackbar.make(
                binding.root,
                getString(R.string.save_error, e.message ?: "?"),
                Snackbar.LENGTH_LONG,
            ).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMarkdownPreviewBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        file = File(intent.getStringExtra(EXTRA_FILE_PATH).orEmpty())
        if (!file.exists()) {
            finish()
            return
        }
        markdown = file.readText()
        supportActionBar?.subtitle = file.name

        Markwon.builder(this)
            .usePlugin(TablePlugin.create(this))
            .build()
            .setMarkdown(binding.markdownText, markdown)
    }

    private fun share() {
        val uri = FileProvider.getUriForFile(
            this, "${BuildConfig.APPLICATION_ID}.fileprovider", file)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/markdown"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, file.name)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(send, getString(R.string.share_report)))
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_preview, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean = when (item.itemId) {
        R.id.action_save_md -> {
            createDocument.launch(file.name)
            true
        }
        R.id.action_share -> {
            share()
            true
        }
        else -> super.onOptionsItemSelected(item)
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    companion object {
        const val EXTRA_FILE_PATH = "filePath"
    }
}
