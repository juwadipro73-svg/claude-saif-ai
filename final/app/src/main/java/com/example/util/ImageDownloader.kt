/* Copyright (c) SAIF AI. All rights reserved. */

package com.example.util

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.io.OutputStream
import java.net.URL

object ImageDownloader {
    suspend fun downloadImageToGallery(context: Context, imageUrl: String) {
        withContext(context = Dispatchers.IO) {
            try {
                val url = URL(imageUrl)
                val connection = url.openConnection()
                connection.doInput = true
                connection.connect()
                val input: InputStream = connection.getInputStream()
                val bitmap = BitmapFactory.decodeStream(input)

                if (bitmap != null) {
                    saveImageToMediaStore(context, bitmap)
                } else {
                    withContext(context = Dispatchers.Main) {
                        Toast.makeText(context, "Failed to decode image", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (err: Exception) {
                withContext(context = Dispatchers.Main) {
                    Toast.makeText(context, "Error saving image: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private suspend fun saveImageToMediaStore(context: Context, bitmap: Bitmap) {
        val filename = "SAIF_AI_${System.currentTimeMillis()}.png"
        var fos: OutputStream? = null
        var imageUri: Uri? = null
        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
            put(MediaStore.MediaColumns.MIME_TYPE, "image/png")
            if (Build.VERSION.SDK_INT >= 29) {
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES)
                put(MediaStore.MediaColumns.IS_PENDING, 1 /* pending */)
            }
        }

        val resolver = context.resolver
        
        try {
            resolver.also { resolver ->
                imageUri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
                fos = imageUri?.let { resolver.openOutputStream(it) }
            }

            fos?.use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }

            contentValues.clear()
            if (Build.VERSION.SDK_INT >= 29) {
                contentValues.put(MediaStore.MediaColumns.IS_PENDING, 0)
                imageUri?.let { resolver.update(it, contentValues, null, null) }
            }

            withContext(context = Dispatchers.Main) {
                Toast.makeText(context, "Image saved to Gallery", Toast.LENGTH_SHORT).show()
            }
        } catch (err: Exception) {
             withContext(context = Dispatchers.Main) {
                Toast.makeText(context, "Failed to save: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
