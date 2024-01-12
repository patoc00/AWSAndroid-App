package com.example.awsandroid

import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.FragmentActivity
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton

class PostRecyclerViewAdapter(
    private val values: MutableList<UserData.Note>?,
    private val listen: OnPostClickListener
) :
    RecyclerView.Adapter<PostRecyclerViewAdapter.ViewHolder>() {

    interface OnPostClickListener {
        fun onPostClick(note: UserData.Note)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.content_note, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {

        val item = values?.get(position)
        holder.nameView.text = item?.name
        holder.descriptionView.text = item?.description


        if (item?.image != null) {
            holder.imageView.setImageBitmap(item.image)
        }
        holder.bind(item)
    }

    override fun getItemCount() = values?.size ?: 0

    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val imageView: ImageView = view.findViewById(R.id.image)
        val nameView: TextView = view.findViewById(R.id.name)
        val descriptionView: TextView = view.findViewById(R.id.description)

        fun bind(item: UserData.Note?) {
            nameView.text = item?.name
            descriptionView.text = item?.description
            item?.image?.let { imageView.setImageBitmap(it) }
            itemView.setOnClickListener { item?.let { listen.onPostClick(it) } }
            nameView.setOnClickListener { item?.let { listen.onPostClick(it) } }
            descriptionView.setOnClickListener { item?.let { listen.onPostClick(it) } }
        }
    }
}


