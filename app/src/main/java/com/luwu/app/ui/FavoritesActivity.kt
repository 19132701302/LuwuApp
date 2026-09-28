package com.luwu.app.ui

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.luwu.app.R

class FavoritesActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        startActivity(CategoryPostsActivity.modeIntent(this, "favorites", getString(R.string.my_favorites)))
        finish()
    }
}
