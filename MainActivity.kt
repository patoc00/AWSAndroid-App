package com.example.awsandroid
import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.AdapterView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Observer
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.android.synthetic.main.content_main.*

@Suppress("DEPRECATION")
class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        setSupportActionBar(toolbar)

        // prepare our List view and RecyclerView (cells)
        setupRecyclerView(item_list)

        setupAuthButton(UserData)
        // update GUI
        UserData.isSignedIn.observe(this, Observer<Boolean> { isSignedUp ->

            Log.i(TAG, "isSignedIn changed : $isSignedUp")

            if (isSignedUp) {
                fabAuth.setImageResource(R.drawable.ic_baseline_lock_open)
                Log.d(TAG, "Showing fabADD")
                fabAdd.show()
                fabAdd.animate().translationY(0.0F - 1.1F * fabAuth.customSize)
            } else {
                fabAuth.setImageResource(R.drawable.ic_baseline_lock)
                Log.d(TAG, "Hiding fabADD")
                fabAdd.hide()
                fabAdd.animate().translationY(0.0F)
            }
        })

        // register a click listener
        fabAdd.setOnClickListener {
            //instance of kotlin class ("KOTLINCLASS::class") converted to java reference (".java")
            startActivity(Intent(this, AddNoteActivity::class.java))
        }
    }

      //receive the web redirect after authentication
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        Backend.handleWebUISignInResponse(requestCode, resultCode, data)
    }

    fun openSomeActivityForResult() {
        val intent = Intent(this, Backend::class.java)
        resultLauncher.launch(intent)
    }

    var resultLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            // There are no request code method getter
            val data: Intent? = result.data
            val requestCode: Int = 7127
            Backend.handleWebUISignInResponse(requestCode, result.resultCode, data)
        }
    }

    // recycler view is the list of cells
    private fun setupRecyclerView(recyclerView: RecyclerView) {

        // update individual cell when the Note data are modified
        UserData.posts().observe(this, Observer<MutableList<UserData.Note>> { posts ->
            Log.d(TAG, "Note observer received ${posts.size} posts")
            recyclerView.adapter = PostRecyclerViewAdapter(posts, object : PostRecyclerViewAdapter.OnPostClickListener {
                override fun onPostClick(note: UserData.Note) {
                    println("Hello from onPostClick: clicked " + note)
                    // Open a dialog window to allow users to input new comments
                    val dialog = CommentDialogFragment(note)
                    dialog.show(supportFragmentManager, "CommentDialogFragment")
                }
            })

        })

        // add a touch gesture handler to manager the swipe to delete gesture
        val itemTouchHelper = ItemTouchHelper(SwipeCallback(this))
        itemTouchHelper.attachToRecyclerView(recyclerView)
    }

    private fun setupAuthButton(userData: UserData) {

        // register a click listener
        fabAuth.setOnClickListener { view ->

            val authButton = view as FloatingActionButton
            //!! means sure it's not null
            if (userData.isSignedIn.value!!) {
                authButton.setImageResource(R.drawable.ic_baseline_lock) // lets see if closed works betetr than open
                Backend.signOut()
            } else {
                authButton.setImageResource(R.drawable.ic_baseline_lock_open)
                Backend.signIn(this)
            }
        }
    }


    companion object {
        private const val TAG = "MainActivity"
    }
}