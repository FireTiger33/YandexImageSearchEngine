package com.stacktivity.yandeximagesearchengine.ui.adapter.viewHolders

import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.text.method.LinkMovementMethod
import android.util.Log
import android.view.View
import androidx.recyclerview.widget.RecyclerView
import com.stacktivity.yandeximagesearchengine.R
import com.stacktivity.yandeximagesearchengine.data.model.Preview
import com.stacktivity.yandeximagesearchengine.data.model.SerpItem
import com.stacktivity.yandeximagesearchengine.ui.adapter.SimpleImageListAdapter
import com.stacktivity.yandeximagesearchengine.util.*
import com.stacktivity.yandeximagesearchengine.util.FileWorker.Companion.saveStringListToFile
import kotlinx.android.synthetic.main.item_image_list.view.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.io.File


/**
 * @param maxImageWidth - max preferred width resolution of image
 * @param defaultColor - color of preview image during loading
 */
class ImageItemViewHolder(
    itemView: View,
    private val eventListener: EventListener,
    private val maxImageWidth: Int,
    private val defaultColor: Int
) : RecyclerView.ViewHolder(itemView), SimpleImageListAdapter.EventListener {

    interface EventListener {
        fun onImageLoadFailed(item: SerpItem)
    }

    interface ContentProvider {
        fun setAddItemList(list: List<String>)
        fun getAddItemCount(): Int
        fun getAddItemOnPosition(position: Int): String
        fun deleteAddItem(item: String): Int
    }

    private lateinit var item: SerpItem
    private lateinit var contentProvider: ContentProvider
    private var currentPreviewNum: Int = -1
    private val otherImageListAdapter = SimpleImageListAdapter(
        this, defaultColor
    )
    private var isShownOtherImages = false
    private val downloadImageTimeout = 3000
    private var job: Job? = null
    private var parserJob: Job? = null

    init {
        itemView.other_image_list_rv.adapter = otherImageListAdapter
    }

    fun bind(item: SerpItem, bufferFile: File, contentProvider: ContentProvider) {
        this.item = item
        this.contentProvider = contentProvider
        reset(bufferFile.path)
        var preview = getNextPreview()!!
        bindTextViews(preview)
        if (bufferFile.exists()) {
            val imageBitmap = BitmapUtils.getBitmapFromFile(bufferFile)
            if (imageBitmap != null) {
                prepareImageView(imageBitmap.width, imageBitmap.height)
                applyBitmapToView(imageBitmap)
                return
            }
        }

        Log.d("bind", "item: $item")
        preview = getMaxAllowSizePreview(preview)
        prepareImageView(preview.w, preview.h)

        job = GlobalScope.launch(Dispatchers.Main) {
            var imageBitmap: Bitmap? = getImageBitmap(preview)
            var previewHasChanged = false
            val anotherPreviewBitmap: Pair<Preview?, Bitmap?>

            if (imageBitmap == null) {
                anotherPreviewBitmap = getAnotherBitmap()
                if (anotherPreviewBitmap.second != null) {
                    preview = anotherPreviewBitmap.first ?: preview
                    imageBitmap = anotherPreviewBitmap.second
                }
                previewHasChanged = true
            }

            if (imageBitmap != null) {
                BitmapUtils.saveBitmapToFile(imageBitmap, bufferFile)

                Log.d("ImageViewHolder",
                    "apply: ${preview.origin?.url
                        ?: preview.url}, currentPreview: $currentPreviewNum, currentDup: ${currentPreviewNum - item.preview.size}"
                )
            } else {
                eventListener.onImageLoadFailed(item)
                return@launch
            }

            Handler(Looper.getMainLooper()).post {
                if (previewHasChanged) {
                    prepareImageView(preview.w, preview.h)
                }
                applyBitmapToView(imageBitmap)
            }
        }
    }

    override fun onImagesLoadFailed() {
        shortToast(R.string.images_load_failed)
        resetOtherImagesView()
    }

    private fun reset(otherImagesBufferFileBase: String) {
        Log.d("ImageViewHolder", "maxImageWidth: $maxImageWidth")
        resetOtherImagesView()
        job?.cancel()
        parserJob?.cancel()
        currentPreviewNum = -1
        val keyFile = File("${otherImagesBufferFileBase}_list")

        itemView.setOnClickListener {
            if (isShownOtherImages) {
                resetOtherImagesView()
            } else {
                itemView.setBackgroundColor(defaultColor)  // TODO databinding
                isShownOtherImages = true
                if (keyFile.exists()) {
                    Log.d("ImageItemViewHolder", "load other images from buffer")

                    showOtherImages(otherImagesBufferFileBase)
                } else {
                    itemView.progress_bar.visibility = View.VISIBLE
                    parserJob = GlobalScope.launch(Dispatchers.Main) {
                        val parentSiteUrl = YandexImageUtil.getImageSourceSite(item)
                        Log.d("ImageItemViewHolder", "parent: $parentSiteUrl")

                        if (parentSiteUrl != null) {
                            val imageLinkList = ImageParser.getUrlListToImages(parentSiteUrl)
                            contentProvider.setAddItemList(imageLinkList)
                            showOtherImages(otherImagesBufferFileBase)
                            FileWorker.createFile(keyFile)
                        } else {
                            // TODO show captcha
                            Log.d("ImageItemViewHolder", "Yandex bot error")
                            resetOtherImagesView()
                            longToast(R.string.yandex_bot_error)
                        }

                        Handler(Looper.getMainLooper()).post {
                            itemView.progress_bar.visibility = View.GONE
                        }
                    }
                }
            }
        }
    }

    private fun showOtherImages(otherImagesBufferFileBase: String) {
        if (contentProvider.getAddItemCount() > 0) {
            otherImageListAdapter.bufferFileBase = otherImagesBufferFileBase
            itemView.other_image_list_rv.visibility = View.VISIBLE
        } else {
            resetOtherImagesView()
            shortToast(R.string.other_images_not_found)
        }
    }

    private fun applyBitmapToView(imageBitmap: Bitmap) {
        itemView.image.run {
            setImageBitmap(imageBitmap)
            colorFilter = null
        }
    }

    private fun resetOtherImagesView() {
        itemView.background = null
        itemView.progress_bar.visibility = View.GONE
        isShownOtherImages = false
        itemView.other_image_list_rv.visibility = View.GONE
        otherImageListAdapter.setNewContentProvider(object : SimpleImageListAdapter.ContentProvider {
            override fun getItemCount(): Int {
                return contentProvider.getAddItemCount()
            }

            override fun getItemOnPosition(position: Int): String {
                return contentProvider.getAddItemOnPosition(position)
            }

            override fun deleteItem(imageUrl: String): Int {
                return contentProvider.deleteAddItem(imageUrl)
            }
        })
    }

    /**
     * Attempt to load another image clone if the corresponding one failed to load.
     *
     * @return Pair<?, null> if attempt failed
     * @return Pair of preview from which the bitmap was obtained and bitmap itself if
     * download is successful
     */
    private suspend fun getAnotherBitmap(): Pair<Preview?, Bitmap?> {
        val previewNum = currentPreviewNum
        var currentPreview: Preview?
        var imageBitmap: Bitmap? = null

        while (getPrevPreview().also { currentPreview = it } != null && imageBitmap == null) {
            if (currentPreview != null) {
                imageBitmap = getImageBitmap(currentPreview!!)
            }
        }
        if (imageBitmap == null) {
            currentPreviewNum = previewNum
            while (getNextPreview().also { currentPreview = it } != null && imageBitmap == null) {
                imageBitmap = getImageBitmap(currentPreview!!)
            }
        }

        return currentPreview to imageBitmap
    }

    private fun getMaxAllowSizePreview(currentPreview: Preview): Preview {
        var preview = currentPreview
        var nullablePreview = getNextPreview()
        while (preview.w > maxImageWidth && nullablePreview != null) {
            nullablePreview = getNextPreview()
            if (nullablePreview != null) {
                preview = nullablePreview
            }
        }

        return preview
    }

    private fun getNextPreview(): Preview? {
        var preview: Preview? = null
        val previewCount = item.preview.size
        val dupsPreviewCount = item.dups.size
        val currentPossibleDupsPreviewNum = currentPreviewNum - previewCount + 1

        if (currentPreviewNum < previewCount - 1) {
            preview = item.preview[++currentPreviewNum]
        } else if (currentPossibleDupsPreviewNum < dupsPreviewCount - 1) {
            preview = item.dups[currentPossibleDupsPreviewNum]
            ++currentPreviewNum
        }

        return preview
    }

    private fun getPrevPreview(): Preview? {
        var preview: Preview? = null
        val previewCount = item.preview.size

        if (currentPreviewNum >= previewCount - 1) {
            preview = item.dups[--currentPreviewNum]
        } else if (currentPreviewNum > 0) {
            preview = item.preview[--currentPreviewNum]
        }

        return preview
    }

    private suspend fun getImageBitmap(preview: Preview): Bitmap? {
        val imageUrl: String = preview.origin?.url ?: preview.url
        var reqWidth: Int? = null
        var reqHeight: Int? = null
        if (preview.w > maxImageWidth) {  // TODO
            val cropFactor = maxImageWidth.toFloat() / preview.w
            reqWidth = maxImageWidth
            reqHeight = (preview.h * cropFactor).toInt()
        }

        return ImageDownloadHelper.getBitmapAsync(imageUrl, reqWidth, reqHeight, downloadImageTimeout)
    }

    private fun prepareImageView(width: Int, height: Int) {
        itemView.image.run {
            val cropFactor: Float = maxImageWidth.toFloat() / width
            val cropHeight: Int = (cropFactor * height).toInt()
            layoutParams.height = cropHeight
            setColorFilter(defaultColor)
        }
    }

    private fun bindTextViews(preview: Preview) {
        val imageResolutionText = "resolution : ${preview.w}x${preview.h}"
        val imageSizeText = "size: ${preview.fileSizeInBytes / 1024}Kb"
        itemView.run {
            title.text = item.snippet.title
            image_resolution.text = imageResolutionText
            image_size.text = imageSizeText
            link.text = preview.origin?.url?: preview.url
            link.movementMethod = LinkMovementMethod.getInstance()
        }
    }
}