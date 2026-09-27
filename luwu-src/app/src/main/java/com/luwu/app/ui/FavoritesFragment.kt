package com.luwu.app.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.luwu.app.R
import com.luwu.app.adapter.PostAdapter
import com.luwu.app.api.PostItem
import com.luwu.app.util.Prefs

/** 收藏页：本地收藏列表 */
class FavoritesFragment : Fragment() {

    private var recycler: RecyclerView? = null
    private var swipe: SwipeRefreshLayout? = null
    private var emptyView: LinearLayout? = null
    private var adapter: PostAdapter? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_favorites, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        swipe = view.findViewById(R.id.swipe_fav)
        recycler = view.findViewById(R.id.recycler_fav)
        emptyView = view.findViewById(R.id.fav_empty)

        recycler?.layoutManager = LinearLayoutManager(requireContext())
        adapter = PostAdapter(
            onPostClick = { openDetail(it) },
            onAdClick = { },
        )
        recycler?.adapter = adapter
        swipe?.setOnRefreshListener { render() }
        render()
    }

    override fun onResume() {
        super.onResume()
        // 从详情页收藏/取消收藏返回后刷新
        render()
    }

    private fun render() {
        val favs = Prefs.getFavorites(requireContext())
        swipe?.isRefreshing = false
        adapter?.setPosts(favs, null, 0)
        emptyView?.visibility = if (favs.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun openDetail(post: PostItem) {
        startActivity(ArticleDetailActivity.newIntent(requireContext(), post))
    }
}
