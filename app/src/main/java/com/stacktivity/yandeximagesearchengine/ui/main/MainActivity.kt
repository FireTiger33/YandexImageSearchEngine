package com.stacktivity.yandeximagesearchengine.ui.main

import android.graphics.Point
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.WindowManager
import androidx.appcompat.widget.PopupMenu
import androidx.appcompat.widget.SearchView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.stacktivity.yandeximagesearchengine.util.prefetcher.PrefetchRecycledViewPool
import com.stacktivity.yandeximagesearchengine.R
import com.stacktivity.yandeximagesearchengine.ui.settings.SettingsActivity
import com.stacktivity.yandeximagesearchengine.ui.captcha.CaptchaDialog
import com.stacktivity.yandeximagesearchengine.util.Constants
import com.stacktivity.yandeximagesearchengine.util.ToolbarDemonstrator
import com.stacktivity.yandeximagesearchengine.util.hideKeyboard
import kotlinx.android.synthetic.main.main_activity.*

private const val KEY_QUERY = "query"

class MainActivity : AppCompatActivity(), SearchView.OnQueryTextListener {
    private val viewModel: MainViewModel = MainViewModel.getInstance()
    private lateinit var searchView: SearchView
    private var showedMenu: PopupMenu? = null

    companion object {
        private var viewPool: PrefetchRecycledViewPool? = null
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.main_activity)
        setSupportActionBar(searchToolBar)
        supportActionBar?.setDisplayShowTitleEnabled(false)
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN)

        initUI(savedInstanceState)
    }

    override fun onStop() {
        showedMenu?.dismiss()
        super.onStop()
    }

    private fun initUI(savedInstanceState: Bundle?) {
        setupSearchView(
            setFocus = savedInstanceState == null,
            savedQuery = savedInstanceState?.getString(KEY_QUERY, "")
        )
        setupImageList()
        setupObservers()
    }

    private fun setupSearchView(setFocus: Boolean, savedQuery: String?) {
        searchView = searchToolBar.findViewById(R.id.search)
        searchView.run {
            setQuery(savedQuery, false)
            setOnQueryTextListener(this@MainActivity)
            isFocusable = true
            isIconified = false
            if (setFocus) {
                requestFocusFromTouch()
            } else {
                clearFocus()
            }
        }
    }

    private fun setupImageList() {
        val size = Point()
        windowManager.defaultDisplay.getSize(size)
        val maxImageWidth = size.x

        setupViewPool()

        val layoutManager = image_list_rv.layoutManager as LinearLayoutManager
        layoutManager.recycleChildrenOnDetach = true
        image_list_rv.adapter = viewModel.getImageItemListAdapter(maxImageWidth).apply {
            prefetchViewHolders(viewPool!!)
        }

        image_list_rv.addOnScrollListener(getImageScrollListener(layoutManager))
    }

    private fun setupViewPool() {
        if (viewPool == null) {
            viewPool = PrefetchRecycledViewPool(this).apply {
                prepare()
            }
        }
        image_list_rv.setRecycledViewPool(viewPool)
    }

    private fun setupObservers() {
        viewModel.dataLoading.observe(this, {
            progress_bar.visibility =
                if (it) View.VISIBLE
                else View.GONE
        })

        viewModel.newQueryIsLoaded.observe(this, {
            if (it && image_list_rv.childCount > 0) {
                image_list_rv.scrollToPosition(0)
            }
        })

        viewModel.captchaEvent.observe(this, {
            it.getContentIfNotHandled()?.let { imageUrl ->
                val dialog = CaptchaDialog.getInstance(
                    imageUrl = imageUrl,
                    showFailedMsg = it.isRepeatEvent
                ) { captchaValue ->
                    it.setResult(captchaValue)
                }

                dialog.show(supportFragmentManager.beginTransaction(), CaptchaDialog.tag)
            }
        })

        viewModel.showedMenu.observe(this, {  // TODO
            showedMenu = it
        })
    }

    private fun getImageScrollListener(layoutManager: LinearLayoutManager): RecyclerView.OnScrollListener {
        return object : RecyclerView.OnScrollListener() {
            var mLastFirstVisibleItem = 0

            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                super.onScrolled(recyclerView, dx, dy)
                val currentFirstVisibleItem = layoutManager.findFirstVisibleItemPosition()
                val lastVisibleItemPosition: Int = layoutManager.findLastVisibleItemPosition()
                val itemCount = layoutManager.itemCount

                // Show / hide SearchToolbar
                if (currentFirstVisibleItem > this.mLastFirstVisibleItem) {
                    ToolbarDemonstrator.hideActionBar(searchToolBar, supportActionBar!!, 200)
                } else if (currentFirstVisibleItem < this.mLastFirstVisibleItem) {
                    ToolbarDemonstrator.showActionBar(searchToolBar, supportActionBar!!, 200)
                }
                this.mLastFirstVisibleItem = currentFirstVisibleItem

                // Request to load a new batch of images when 40% of the current batch is reached
                if (itemCount - lastVisibleItemPosition <= Constants.PAGE_SIZE * 0.4) {
                    viewModel.fetchImagesOnNextPage()
                }

                progress_bar.visibility =
                    if (viewModel.dataLoading.value != false && lastVisibleItemPosition + 1 == layoutManager.itemCount) View.VISIBLE
                    else View.GONE
            }
        }
    }

    override fun onQueryTextSubmit(query: String?): Boolean {
        if (query != null) {
            hideKeyboard(this, searchView)
            searchView.clearFocus()
            viewModel.fetchImagesOnQuery(query)
        }

        return true
    }

    override fun onQueryTextChange(newText: String?): Boolean {
        return false
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.main, menu)

        return super.onCreateOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.settings -> {
                SettingsActivity.start(this)
                true
            }

            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString(KEY_QUERY, searchView.query.toString())
        super.onSaveInstanceState(outState)
    }

    override fun onNightModeChanged(mode: Int) {
        viewPool?.clear()
        viewPool = null
    }
}