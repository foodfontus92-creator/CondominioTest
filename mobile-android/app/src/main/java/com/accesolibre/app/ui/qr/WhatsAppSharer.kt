package com.accesolibre.app.ui.qr

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import androidx.core.content.FileProvider
import com.accesolibre.app.domain.model.Pass
import java.io.File
import java.io.FileOutputStream
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

object WhatsAppSharer {

    private val formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")

    fun sharePassImage(context: Context, pass: Pass, bitmap: Bitmap) {
        val dir = File(context.cacheDir, "shared_qr").apply { mkdirs() }
        val file = File(dir, "pass_${pass.passId.take(8)}.png")
        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)

        val expFormatted = Instant.ofEpochMilli(pass.expiresAtMs)
            .atZone(ZoneId.systemDefault())
            .format(formatter)
        val caption = "Pase de acceso AccesoLibre para ${pass.visitorName}.\nVálido hasta: $expFormatted"

        val baseIntent = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_TEXT, caption)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        val whatsAppPackage = findInstalledWhatsAppPackage(context)
        if (whatsAppPackage != null) {
            baseIntent.setPackage(whatsAppPackage)
            runCatching {
                context.startActivity(baseIntent)
                return
            }
            baseIntent.setPackage(null)
        }

        val chooser = Intent.createChooser(baseIntent, "Compartir pase QR")
        context.startActivity(chooser)
    }

    private fun findInstalledWhatsAppPackage(context: Context): String? {
        val pm = context.packageManager
        for (pkg in listOf("com.whatsapp", "com.whatsapp.w4b")) {
            val installed = runCatching {
                pm.getPackageInfo(pkg, PackageManager.PackageInfoFlags.of(0))
            }.isSuccess || runCatching {
                @Suppress("DEPRECATION")
                pm.getPackageInfo(pkg, 0)
            }.isSuccess
            if (installed) return pkg
        }
        return null
    }
}
