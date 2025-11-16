package it.palsoftware.pastiera

import android.graphics.Color
import android.graphics.Typeface
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import androidx.appcompat.app.AppCompatDelegate

/**
 * Adapter for Unicode character RecyclerView.
 * Optimized for performance using classic RecyclerView.
 */
class UnicodeCharacterRecyclerViewAdapter(
    private val characters: List<String>,
    private val onCharacterClick: (String) -> Unit
) : RecyclerView.Adapter<UnicodeCharacterRecyclerViewAdapter.CharacterViewHolder>() {

    class CharacterViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val characterText: TextView = itemView.findViewById(android.R.id.text1)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CharacterViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(android.R.layout.simple_list_item_1, parent, false)
        val holder = CharacterViewHolder(view)
        
        // Configure TextView to center character, theme-aware color and bold
        holder.characterText.apply {
            textSize = 24f
            gravity = android.view.Gravity.CENTER
            setPadding(0, 0, 0, 0)
            minHeight = (40 * parent.context.resources.displayMetrics.density).toInt()
            minWidth = (40 * parent.context.resources.displayMetrics.density).toInt()
            // Use theme-aware text color: black for light theme, white for dark theme
            val isDarkTheme = AppCompatDelegate.getDefaultNightMode() == AppCompatDelegate.MODE_NIGHT_YES ||
                    (AppCompatDelegate.getDefaultNightMode() == AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM &&
                     (parent.context.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES)
            setTextColor(if (isDarkTheme) Color.WHITE else Color.BLACK)
            setTypeface(null, Typeface.BOLD)
        }
        
        // Click listener setup
        view.setOnClickListener {
            val position = holder.bindingAdapterPosition
            if (position != RecyclerView.NO_POSITION && position < characters.size) {
                onCharacterClick(characters[position])
            }
        }
        
        return holder
    }

    override fun onBindViewHolder(holder: CharacterViewHolder, position: Int) {
        holder.characterText.text = characters[position]
    }

    override fun getItemCount(): Int = characters.size
}


