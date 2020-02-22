package com.stacktivity.yandeximagesearchengine.ui.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.stacktivity.yandeximagesearchengine.R
import com.stacktivity.yandeximagesearchengine.data.model.ImageItem
import com.stacktivity.yandeximagesearchengine.ui.adapter.viewHolders.ImageItemViewHolder
import java.io.File

class ImageListAdapter(
    private val contentProvider: ContentProvider,
    private val imageBufferFilesDir: File,
    private val eventListener: ImageItemViewHolder.EventListener,
    private val maxImageWidth: Int,
    private val defaultColor: Int
) : RecyclerView.Adapter<ImageItemViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ImageItemViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        val view = inflater.inflate(R.layout.item_image_list, parent, false)
        return ImageItemViewHolder(view, eventListener, maxImageWidth, defaultColor)
    }

    override fun getItemCount() = contentProvider.getItemCount()

    override fun onBindViewHolder(holder: ImageItemViewHolder, positionVh: Int) {
        val bufferFile = File(imageBufferFilesDir.path + File.separator + positionVh)
        holder.bind(contentProvider.getItemOnPosition(positionVh), bufferFile,
            object : ImageItemViewHolder.ContentProvider {
                override fun setAddItemList(list: List<String>) {
                    contentProvider.setAddImageList(positionVh, list)
                }

                override fun getAddItemCount(): Int {
                    return contentProvider.getAddImagesCountOnPosition(positionVh)
                }

                override fun getAddItemOnPosition(position: Int): String {
                    return contentProvider.getAddImageListItemOnPosition(positionVh, position)
                }

                override fun deleteAddItem(item: String): Int {
                    return contentProvider.deleteItemOtherImageOnPosition(positionVh, item)
                }

            }
        )
    }

    interface ContentProvider {
        fun getItemCount(): Int
        fun getItemOnPosition(position: Int): ImageItem
        fun setAddImageList(position: Int, list: List<String>)
        fun getAddImagesCountOnPosition(position: Int): Int
        fun getAddImageListItemOnPosition(position: Int, itemIndex: Int): String
        fun deleteItemOtherImageOnPosition(position: Int, imageUrl: String): Int
    }
}