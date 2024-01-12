package com.example.awsandroid

import android.graphics.Bitmap
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.amplifyframework.api.graphql.model.ModelMutation
import com.amplifyframework.core.Amplify
import com.amplifyframework.core.model.Model
import com.amplifyframework.core.model.temporal.Temporal
import com.amplifyframework.datastore.generated.model.NoteData
import com.amplifyframework.datastore.generated.model.Comment
import java.sql.Date

// a singleton to hold user data                                                                                                                (this is a ViewModel pattern, without inheriting from ViewModel)
object UserData {

    private const val TAG = "UserData"

    // signed in status
    private val _isSignedIn = MutableLiveData<Boolean>(false)
    //LiveData flag to track current authentication status and update observers
    var isSignedIn: LiveData<Boolean> = _isSignedIn

    fun setSignedIn(newValue : Boolean) {
        // use postvalue() to make the assignation on the main GUI thread
        _isSignedIn.postValue(newValue)
    }

    // the user's posts
    private val _posts = MutableLiveData<MutableList<Note>>(mutableListOf())

    //updates observers which updates GUI
    private fun <T> MutableLiveData<T>.notifyObserver() {
        this.postValue(this.value)
    }
    fun notifyObserver() {
        this._posts.notifyObserver()
    }

    fun posts() : LiveData<MutableList<Note>>  = _posts
    fun addNote(n : Note) {
        val posts = _posts.value
        if (posts != null) {
            //adds post to mutable list
            posts.add(n)
            //updates GUI
            _posts.notifyObserver()
        } else {
            Log.e(TAG, "addNote : note collection is null !!")
        }
    }
    fun deleteNote(at: Int) : Note?  {
        //removes note at a specific index of the mutable list
        val note = _posts.value?.removeAt(at)
        //updates GUI
        _posts.notifyObserver()
        return note
    }

    fun resetposts() {
        this._posts.value?.clear()  //used when signing out
        _posts.notifyObserver()
    }

    // one comment data class
    data class Comments(val parentPost: String, val text: String){
        val data : Comment
            get() = Comment.builder()
                .parentPost(this.parentPost)
                .text(this.text)
                .build()

        // static function to create and return a Comments object from the Comment API object.
        companion object {
            fun from(c : Comment) : Comments {
                val result = Comments(c.parentPost, c.text)
                return result
            }
        }
    }

    // single post's data class
    data class Note(val id: String, val name: String, val description: String, var imageName: String? = null, val comments: MutableList<Comments> = mutableListOf()) {
        override fun toString(): String = name

        // bitmap image
        var image : Bitmap? = null

            // return an API NoteData object from Note object
           val data : NoteData
               get() = NoteData.builder()
                   .name(this.name)
                   .description(this.description)
                   .image(this.imageName)
                   .id(this.id)
                   .build()

           // static function to create and return a Note object from a NoteData API object
          companion object {
               fun from(noteData : NoteData) : Note {
                   val result = Note(noteData.id, noteData.name, noteData.description, noteData.image)

                   if (noteData.image != null) {
                       Backend.retrieveImage(noteData.image!!) {
                           result.image = it

                           // force a UI update
                           with(UserData) { notifyObserver() }
                       }
                   }
                   return result
               }
           }

    }

    fun updatePost(postComments: Comments) {

        Amplify.API.mutate(
            ModelMutation.create(postComments.data),
            { response ->
                Log.i("UserData.updatePost", "Created Comment")
            },
            { error -> Log.e("UserData.updatePost", "Create Comment failed", error) }
        )
    }
}