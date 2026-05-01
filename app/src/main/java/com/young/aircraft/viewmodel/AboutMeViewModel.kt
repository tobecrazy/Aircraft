package com.young.aircraft.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.young.aircraft.R

class AboutMeViewModel(
    val repoUrl: String,
    val repoLine: String,
    val developerParagraphs: List<String>,
    val projectParagraphs: List<String>
) : ViewModel() {

    class Factory(context: Context) : ViewModelProvider.Factory {
        private val ctx = context.applicationContext
        private val repoUrl = ctx.getString(R.string.about_me_project_repo_url)
        private val repoLine = ctx.getString(
            R.string.about_me_project_repo_line,
            ctx.getString(R.string.about_github_label),
            repoUrl
        )
        private val developerContent = ctx.getString(R.string.about_me_content)
        private val projectContent = ctx.getString(R.string.about_me_project_content, repoLine)

        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return AboutMeViewModel(
                repoUrl = repoUrl,
                repoLine = repoLine,
                developerParagraphs = toParagraphs(developerContent),
                projectParagraphs = toParagraphs(projectContent)
            ) as T
        }

        private fun toParagraphs(text: String): List<String> {
            return text.split("\n\n").map(String::trim).filter(String::isNotEmpty)
        }
    }
}
