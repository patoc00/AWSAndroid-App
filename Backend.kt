package com.example.awsandroid

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import com.amazonaws.regions.Region
import com.amplifyframework.AmplifyException
import com.amplifyframework.api.aws.AWSApiPlugin
import com.amplifyframework.api.graphql.model.ModelMutation
import com.amplifyframework.api.graphql.model.ModelQuery
import com.amplifyframework.auth.AuthCategory
import com.amplifyframework.auth.AuthCategoryBehavior
import com.amplifyframework.auth.AuthChannelEventName
import com.amplifyframework.auth.AuthException
import com.amplifyframework.auth.cognito.AWSCognitoAuthPlugin
import com.amplifyframework.auth.cognito.AWSCognitoAuthSession
import com.amplifyframework.auth.cognito.result.AWSCognitoAuthSignOutResult
import com.amplifyframework.auth.options.AuthSignOutOptions
import com.amplifyframework.auth.result.AuthSessionResult
import com.amplifyframework.auth.result.AuthSignInResult
import com.amplifyframework.auth.result.AuthSignOutResult
import com.amplifyframework.core.Amplify
import com.amplifyframework.core.InitializationStatus
import com.amplifyframework.datastore.generated.model.NoteData
import com.amplifyframework.hub.HubChannel
import com.amplifyframework.hub.HubEvent
import com.amplifyframework.storage.StorageAccessLevel
import com.amplifyframework.storage.options.StorageDownloadFileOptions
import com.amplifyframework.storage.options.StorageRemoveOptions
import com.amplifyframework.storage.options.StorageUploadFileOptions
import com.amplifyframework.storage.s3.AWSS3StoragePlugin
import java.io.File
import java.io.FileInputStream



//singleton design pattern
object Backend {

    private const val TAG = "Backend"
    private const val WEB_UI_SIGN_IN_ACTIVITY_CODE = 7127

    fun initialize(applicationContext: Context) : Backend {
        //Initializes amplify
        try {
            Amplify.addPlugin(AWSCognitoAuthPlugin())
            Amplify.addPlugin(AWSApiPlugin())
            //Amplify.addPlugin(AWSS3StoragePlugin())
            try {
                Amplify.configure(applicationContext)
                Log.i(TAG, "Initialized Amplify")
            } catch (exception: AmplifyException) {
                Log.e("Amplify", "Failed to configure Amplify", exception)
            }
        } catch (e: AmplifyException) {
            Log.e(TAG, "Could not initialize Amplify", e)
        }
        Log.i(TAG, "registering hub event")

        // listen to authentication event with hub listener
        Amplify.Hub.subscribe(HubChannel.AUTH) { hubEvent: HubEvent<*> ->

            when (hubEvent.name) {
                InitializationStatus.SUCCEEDED.toString() -> {
                    Log.i(TAG, "Amplify successfully initialized")
                }
                InitializationStatus.FAILED.toString() -> {
                    Log.i(TAG, "Amplify initialization failed")
                }
                else -> {
                    when (AuthChannelEventName.valueOf(hubEvent.name)) {
                        AuthChannelEventName.SIGNED_IN -> {
                            updateUserData(true)
                            Log.i(TAG, "HUB : SIGNED_IN")
                        }
                        AuthChannelEventName.SIGNED_OUT -> {
                            updateUserData(false)
                            Log.i(TAG, "HUB : SIGNED_OUT")
                        }
                        else -> Log.i(TAG, """HUB EVENT:${hubEvent.name}""")
                    }
                }
            }
        }

        Log.i(TAG, "retrieving session status")

// checks if user is already authenticated (from a previous execution)
        Amplify.Auth.fetchAuthSession(
            //Value from signIn authorization session
            { result ->
                Log.i(TAG, result.toString())
                val cognitoAuthSession = result as AWSCognitoAuthSession
                // update GUI
                this.updateUserData(cognitoAuthSession.isSignedIn)
                when (cognitoAuthSession.userSubResult.type) {
                    AuthSessionResult.Type.SUCCESS ->  Log.i(TAG, "IdentityId: " + cognitoAuthSession.userSubResult.value)
                    AuthSessionResult.Type.FAILURE -> Log.i(TAG, "IdentityId not present because: " + cognitoAuthSession.userSubResult.error.toString())
                }
            },
            { error -> Log.i(TAG, error.toString()) }
        )
        return this
    }

    // pass the data from web redirect to Amplify libs
    fun handleWebUISignInResponse(requestCode: Int, resultCode: Int, data: Intent?) {
        Log.d(TAG, "received requestCode : $requestCode and resultCode : $resultCode")
        if (requestCode == WEB_UI_SIGN_IN_ACTIVITY_CODE) {
            Amplify.Auth.handleWebUISignInResponse(data)
        }
    }


    private fun updateUserData(withSignedInStatus : Boolean) {
       //Verifies if user is signed in
        UserData.setSignedIn(withSignedInStatus)

        val posts = UserData.posts().value
        val isEmpty = posts?.isEmpty() ?: false

        // query posts when signed in and we do not have posts yet
        if (withSignedInStatus && isEmpty ) {
            this.queryPost()
        } else {
            UserData.resetposts()
        }
    }

    fun signOut() {
        Log.i(TAG, "Initiate Signout Sequence")
        //Calls amplify signout interface, which calls appsync, then lambda and then cognito
        Amplify.Auth.signOut { signOutResult ->
            when(signOutResult) {
                is AWSCognitoAuthSignOutResult.CompleteSignOut -> {
                    // Sign Out completed fully and without errors.
                    Log.i("AuthQuickStart", "Signed out successfully")
                }
                is AWSCognitoAuthSignOutResult.PartialSignOut -> {
                    // Sign Out completed with some errors. User is signed out of the device.
                    signOutResult.hostedUIError?.let {
                        Log.e("AuthQuickStart", "HostedUI Error", it.exception)
                        // Optional: Re-launch it.url in a Custom tab to clear Cognito web session.

                    }
                    signOutResult.globalSignOutError?.let {
                        Log.e("AuthQuickStart", "GlobalSignOut Error", it.exception)
                        // Optional: Use escape hatch to retry revocation of it.accessToken.
                    }
                    signOutResult.revokeTokenError?.let {
                        Log.e("AuthQuickStart", "RevokeToken Error", it.exception)
                        // Optional: Use escape hatch to retry revocation of it.refreshToken.
                    }
                }
                is AWSCognitoAuthSignOutResult.FailedSignOut -> {
                    // Sign Out failed with an exception, leaving the user signed in.
                    Log.e("AuthQuickStart", "Sign out Failed", signOutResult.exception)
                }
            }
        }
    }

    fun signIn(callingActivity: Activity) {
        Log.i(TAG, "Initiate Signin Sequence")
        //calls Amplify to initiate sign-in flow sequence, which calls AppSync and runs lambda code calling amazon cognito that retrieves JWT (JSON Web Token).
        Amplify.Auth.signInWithWebUI(
            callingActivity,
            { result: AuthSignInResult ->  Log.i(TAG, result.toString()) },  //if succesfully entered credentials, onSucess callback
            { error: AuthException -> Log.e(TAG, error.toString()) }  //if failed, onError callback
        )
    }

    fun storeImage(filePath: String, key: String) {
        val file = File(filePath)
        val options = StorageUploadFileOptions.builder()
            .accessLevel(StorageAccessLevel.PRIVATE)
            .build()
        //val Region = "us-east-1"

        Amplify.Storage.uploadFile(
            key,
            file,
            options,
            { progress -> Log.i(TAG, "Fraction completed: ${progress.fractionCompleted}") },
            { result -> Log.i(TAG, "Successfully uploaded: " + result.key) },
            { error -> Log.e(TAG, "Upload failed", error) }
        )
    }

    fun deleteImage(key : String) {

        val options = StorageRemoveOptions.builder()
            .accessLevel(StorageAccessLevel.PRIVATE)
            .build()

        Amplify.Storage.remove(
            key,
            options,
            { result -> Log.i(TAG, "Successfully removed: " + result.key) },
            { error -> Log.e(TAG, "Remove failure", error) }
        )
    }

    fun retrieveImage(key: String, completed : (image: Bitmap) -> Unit) {
        val options = StorageDownloadFileOptions.builder()
            .accessLevel(StorageAccessLevel.PRIVATE)
            .build()

        val file = File.createTempFile("image", ".image")

        Amplify.Storage.downloadFile(
            key,
            file,
            options,
            { progress -> Log.i(TAG, "Fraction completed: ${progress.fractionCompleted}") },
            { result ->
                Log.i(TAG, "Successfully downloaded: ${result.file.name}")
                val imageStream = FileInputStream(file)
                val image = BitmapFactory.decodeStream(imageStream)
                completed(image)
            },
            { error -> Log.e(TAG, "Download Failure", error) }
        )
    }

    //calling UserData.Note returns a variable note
    fun createPost(note : UserData.Note) {
        Log.i(TAG, "Creating posts")
        println("This is note.data: "+note.data)
        //calls graphQL class that calls helper class and method create that returns a NoteData note.
        Amplify.API.mutate(
            ModelMutation.create(note.data),
            { response ->
                Log.i(TAG, "Created")
                if (response.hasErrors()) {
                    Log.e(TAG, response.errors.first().message)
                } else {
                    Log.i(TAG, "Created Post with id: " + response.data.id)
                }
            },
            { error -> Log.e(TAG, "Create failed", error) }
        )
    }

    fun deletePost(note : UserData.Note?) {

        if (note == null) return

        Log.i(TAG, "Deleting post $note")
        //calls graphQL class that calls helper class, then deletes data from database, returns request instance.
        Amplify.API.mutate(
            ModelMutation.delete(note.data),
            { response ->
                Log.i(TAG, "Deleted")
                if (response.hasErrors()) {
                    Log.e(TAG, response.errors.first().message)
                } else {
                    Log.i(TAG, "Deleted Post $response")
                }
            },
            { error -> Log.e(TAG, "Delete failed", error) }
        )
    }

    fun queryPost() {
        Log.i(TAG, "Querying posts")
        //Amplify calls GraphQl interface which calls a helper class that returns a list of values.
        Amplify.API.query(
            ModelQuery.list(NoteData::class.java),
            { response ->
                Log.i(TAG, "Queried")
                if (response.data != null) {
                    for (noteData in response.data) {
                        Log.i(TAG, noteData.name)
                        // should add all the posts at once, this triggers a UI refresh.
                        UserData.addNote(UserData.Note.from(noteData))
                    }
                } else {
                    Log.e(TAG, "No data found in query response")
                    println(response.toString())
                }
            },
            { error -> Log.e(TAG, "Query failure", error) }
        )
    }

}
