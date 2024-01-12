package com.example.awsandroid

import android.app.Dialog
import android.content.DialogInterface
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class CommentDialogFragment(val post: UserData.Note) : DialogFragment(), DialogInterface.OnClickListener {

    private lateinit var commentEditText: EditText
    private lateinit var commentsRecyclerView: RecyclerView

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        // Inflate the layout for the dialog
        val view = requireActivity().layoutInflater.inflate(R.layout.comment_dialog, null)

        // Get a reference to the EditText for the comment text
        commentEditText = view.findViewById(R.id.comment_input)

        // Get a reference to the RecyclerView for the comments
        commentsRecyclerView = view.findViewById(R.id.comments_recycler_view)

        // Create a LinearLayoutManager and set it as the layout manager for the RecyclerView
        val layoutManager = LinearLayoutManager(requireContext())
        commentsRecyclerView.layoutManager = layoutManager

        // Create a new adapter for the RecyclerView and set it as the adapter for the RecyclerView
        val adapter = CommentsRecyclerViewAdapter(post.comments)
        commentsRecyclerView.adapter = adapter

        // Create the dialog builder and set the dialog properties
        val builder = AlertDialog.Builder(requireActivity())
        builder.setTitle("Comments Section for " + post.toString())
        builder.setView(view)
        builder.setPositiveButton("Add", this)
        builder.setNegativeButton("Close", null)

        // Return the created dialog
        return builder.create()
    }

    override fun onClick(dialog: DialogInterface?, which: Int) {
        when (which) {
            DialogInterface.BUTTON_POSITIVE -> {
                // Get the text of the comment from the EditText
                val commentText = commentEditText.text.toString()

                // Create a new comment object with the text
                val parentPost = post.toString()
                val comment = UserData.Comments(parentPost, commentText)

                // Add the comment to the post's list of comments
                post.comments.add(comment)

                // Update the post in the database
                val commentType = UserData.Comments(post.toString(), comment.toString())
                UserData.updatePost(commentType)

                // Force update GUI
                with(UserData) { notifyObserver() }

                // Update the RecyclerView adapter to reflect the changes in the comments list
                commentsRecyclerView.adapter?.notifyDataSetChanged()

                // Dismiss the dialog
                dismiss()
            }
        }
    }

    inner class CommentsRecyclerViewAdapter(private val comments: MutableList<UserData.Comments>) :
        RecyclerView.Adapter<CommentsRecyclerViewAdapter.ViewHolder>() {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.comment_item, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val item = comments[position]
            holder.commentTextView.text = item.text
        }

        override fun getItemCount(): Int = comments.size

        inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val commentTextView: TextView = view.findViewById(R.id.comment_text)
        }
    }

}
