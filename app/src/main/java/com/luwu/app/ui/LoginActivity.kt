package com.luwu.app.ui

import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.textfield.TextInputLayout
import com.luwu.app.R
import com.luwu.app.api.ApiClient
import com.luwu.app.util.AppState
import com.luwu.app.util.Prefs
import com.luwu.app.util.Util

class LoginActivity : AppCompatActivity() {

    private var etUsername: EditText? = null
    private var etPassword: EditText? = null
    private var tvError: TextView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        etUsername = findViewById(R.id.et_username)
        etPassword = findViewById(R.id.et_password)
        tvError = findViewById(R.id.tv_error)

        // 已登录则预填账号（密码不再本地保存，不预填）
        if (Prefs.isLoggedIn(this)) {
            etUsername?.setText(Prefs.getAccount(this))
        }

        findViewById<View>(R.id.btn_login).setOnClickListener { doLogin() }
        findViewById<View>(R.id.btn_register).setOnClickListener {
            Util.openBrowser(this, "https://www.65gw.com/register.html")
        }
    }

    private fun doLogin() {
        if (AppState.pluginAvailable == false) {
            showError("登录发布功能需要网站启用「陆伍App控制台」插件")
            return
        }
        val name = etUsername?.text?.toString()?.trim() ?: ""
        val pwd = etPassword?.text?.toString() ?: ""
        if (name.isEmpty() || pwd.isEmpty()) {
            showError("请输入账号和密码")
            return
        }
        findViewById<View>(R.id.btn_login).isEnabled = false
        tvError?.visibility = View.GONE

        ApiClient.post("login", mapOf("username" to name, "password" to pwd)) { json, err ->
            findViewById<View>(R.id.btn_login).isEnabled = true
            if (json == null || !json.optBoolean("ok", false)) {
                showError(json?.optString("error", "") ?: err ?: "登录失败")
                return@post
            }
            val uid = json.optLong("uid", 0L)
            val display = json.optString("name", name)
            Prefs.setLogin(this, uid, name, display)
            // 服务端签发的 token 用于后续所有认证请求，密码用完即弃
            Prefs.saveToken(this, json.optString("token", ""))
            Util.toast(this, "登录成功")
            finish()
        }
    }

    private fun showError(msg: String) {
        tvError?.text = msg
        tvError?.visibility = View.VISIBLE
    }
}
