package com.stacktivity.yandeximagesearchengine.ui.main

import android.util.Log
import androidx.lifecycle.MutableLiveData
import com.stacktivity.yandeximagesearchengine.util.YandexImageUtil
import com.stacktivity.yandeximagesearchengine.base.BaseViewModel
import com.stacktivity.yandeximagesearchengine.data.model.ImageData
import com.stacktivity.yandeximagesearchengine.data.model.MainRepository
import com.stacktivity.yandeximagesearchengine.data.model.SerpItem
import com.stacktivity.yandeximagesearchengine.ui.adapter.ImageListAdapter

class MainViewModel : BaseViewModel() {
    val newQueryIsLoaded = MutableLiveData<Boolean>().apply { value = false }
    private var numLoadedPages: Int = 0
    private var currentQuery: String = ""
    private var isLastPage = false

    private val imageList: List<SerpItem>
        get() = MainRepository.getInstance().getImageList()
    private val imageCount: Int
        get() = MainRepository.getInstance().getImageCount()

    private var adapter: ImageListAdapter? = null

    fun getImageItemListAdapter(maxImageWidth: Int): ImageListAdapter = adapter
        ?: ImageListAdapter(object : ImageListAdapter.ContentProvider {
            override fun getItemCount(): Int = imageCount
            override fun getItemOnPosition(position: Int): SerpItem = imageList[position]
        }, maxImageWidth).also {
            adapter = it
        }

    fun fetchImagesOnQuery(query: String) {
        empty.value = true
        isLastPage= false
        numLoadedPages = 0
        currentQuery = query
        fetchImagesOnNextPage()
    }

    fun fetchImagesOnNextPage() {
        if (!dataLoading.value!! && !isLastPage) {
            fetchImages(currentQuery, numLoadedPages)
        }
    }

    private fun fetchImages(query: String, page: Int) {
        dataLoading.value = true
        MainRepository.getInstance().getImageData(query, page) { isSuccess, response: ImageData? ->
            dataLoading.value = false
            if (isSuccess) {
                empty.value = false
                if (response?.blocks != null) {
                    val html = response.blocks[0].html
                    val itemList = YandexImageUtil.getSerpListFromHtml(html)
                    newQueryIsLoaded.value = numLoadedPages < 1
                    numLoadedPages++
                    applyData(itemList)
                } else {
                    // TODO check num of pages

                    isLastPage = true
                }
            }
        }
    }

    /**
     * Change itemList in repository and and notifies the adapter of changes made
     */
    private fun applyData(itemList: List<SerpItem>) {  // TODO remove log
        val repo = MainRepository.getInstance()
        if (newQueryIsLoaded.value != false) {
            repo.clearImageList()
            adapter!!.notifyDataSetChanged()
        }
        val lastImageCount = imageCount
        repo.addToImageList(itemList)
        adapter!!.notifyItemRangeInserted(lastImageCount, imageCount - 1)
    }

    companion object {
        private val tag = MainViewModel::class.java.simpleName

        private var INSTANCE: MainViewModel? = null
        fun getInstance() = INSTANCE
            ?: MainViewModel().also {
                INSTANCE = it
            }
    }
}