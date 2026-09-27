package com.luwu.app.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.luwu.app.R
import com.luwu.app.api.ApiClient
import com.luwu.app.api.PostItem
import com.luwu.app.util.Prefs
import com.luwu.app.util.Util
import org.json.JSONObject

/** 我的订单：查看购买记录（哪个帖子的资源、订单编号、支付方式、金额、状态） */
class OrderActivity : AppCompatActivity() {

    private var recycler: RecyclerView? = null
    private var tvEmpty: TextView? = null
    private var adapter: OrderAdapter? = null
    private val items = mutableListOf<JSONObject>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_order)

        findViewById<View>(R.id.btn_back).setOnClickListener { finish() }
        Util.applyThemeBar(
            findViewById(R.id.top_bar),
            findViewById(R.id.tv_title),
            findViewById(R.id.btn_back),
        )
        recycler = findViewById(R.id.recycler)
        tvEmpty = findViewById(R.id.tv_empty)
        recycler?.layoutManager = LinearLayoutManager(this)
        adapter = OrderAdapter { obj ->
            val cid = obj.optInt("cid", 0)
            if (cid > 0) {
                startActivity(
                    ArticleDetailActivity.newIntent(
                        this,
                        PostItem(cid, obj.optString("title", "文章详情"), "", "", "", "")
                    )
                )
            }
        }
        recycler?.adapter = adapter
        load()
    }

    private fun load() {
        if (Prefs.getUid(this) <= 0) {
            tvEmpty?.text = "请先登录后再查看订单"
            tvEmpty?.visibility = View.VISIBLE
            return
        }
        ApiClient.get("orders", mapOf("token" to Prefs.getToken(this))) { json, err ->
            if (json == null || !json.optBoolean("ok", false)) {
                tvEmpty?.text = err ?: "订单加载失败"
                tvEmpty?.visibility = View.VISIBLE
                return@get
            }
            items.clear()
            val arr = json.optJSONArray("items")
            if (arr != null) {
                for (i in 0 until arr.length()) {
                    arr.optJSONObject(i)?.let { items.add(it) }
                }
            }
            adapter?.submit(items)
            tvEmpty?.visibility = if (items.isEmpty()) View.VISIBLE else View.GONE
        }
    }

    /** 支付方式中文名 */
    private fun payName(type: String): String = when (type) {
        "wxpay", "wechat" -> "微信支付"
        "alipay" -> "支付宝"
        "qqpay" -> "QQ支付"
        else -> "在线支付"
    }

    inner class OrderAdapter(
        private val onItem: (JSONObject) -> Unit
    ) : RecyclerView.Adapter<OrderAdapter.Holder>() {

        private var list: List<JSONObject> = emptyList()

        fun submit(newList: List<JSONObject>) {
            list = newList
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
            val v = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_order, parent, false)
            return Holder(v)
        }

        override fun onBindViewHolder(holder: Holder, position: Int) {
            val o = list[position]
            val paid = o.optInt("status", 0) == 1
            holder.tvTitle.text = o.optString("title", "付费资源")
            holder.tvOrderNo.text = "订单号：${o.optString("trade_no", "-")}"
            holder.tvPay.text = payName(o.optString("type", ""))
            holder.tvMoney.text = "¥${o.optString("money", "0.00")}"
            holder.tvTime.text = o.optString("time", "")
            holder.tvStatus.text = if (paid) "已支付" else "待支付"
            holder.tvStatus.setTextColor(
                if (paid) 0xFF0D9488.toInt() else 0xFFF59E0B.toInt()
            )
            holder.itemView.setOnClickListener { onItem(o) }
        }

        override fun getItemCount(): Int = list.size

        inner class Holder(v: View) : RecyclerView.ViewHolder(v) {
            val tvTitle: TextView = v.findViewById(R.id.tv_order_title)
            val tvOrderNo: TextView = v.findViewById(R.id.tv_order_no)
            val tvPay: TextView = v.findViewById(R.id.tv_order_pay)
            val tvMoney: TextView = v.findViewById(R.id.tv_order_money)
            val tvTime: TextView = v.findViewById(R.id.tv_order_time)
            val tvStatus: TextView = v.findViewById(R.id.tv_order_status)
        }
    }
}
