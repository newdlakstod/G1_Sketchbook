package com.g1.sketchbook.auth

import android.annotation.SuppressLint
import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialException
import com.g1.sketchbook.data.await
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.CancellationException

/**
 * Google sign-in via Credential Manager -> Firebase Auth.
 * [webClientId] is the Firebase "web client" OAuth id (…apps.googleusercontent.com).
 */
class GoogleAuthClient(
    private val webClientId: String,
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
) {
    val currentUser: FirebaseUser? get() = auth.currentUser

    @SuppressLint("CredentialManagerSignInWithGoogle") // 타입 상수를 직접 검사하지만 Lint가 Kotlin 참조를 놓치는 오탐.
    /** [activityContext]는 반드시 Activity context여야 한다 — Application context를 넘기면 계정
     *  선택 바텀시트를 띄울 액티비티/윈도우가 없어서 기기에 따라 그대로 멈추거나(ANR) 죽는다
     *  (Application context로도 예외 없이 통과되는 기기가 있어서 개발 중엔 안 드러났을 뿐, 공식
     *  문서도 Activity context를 요구함). */
    suspend fun signIn(activityContext: Context): Result<FirebaseUser> {
        return try {
            val googleIdOption = GetGoogleIdOption.Builder()
                .setFilterByAuthorizedAccounts(false)
                .setServerClientId(webClientId)
                .setAutoSelectEnabled(true)
                .build()

            val request = GetCredentialRequest.Builder()
                .addCredentialOption(googleIdOption)
                .build()

            val credentialManager = CredentialManager.create(activityContext)
            val response = credentialManager.getCredential(activityContext, request)
            val credential = response.credential

            if (credential !is CustomCredential ||
                credential.type != GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
            ) {
                return Result.failure(IllegalStateException("지원하지 않는 로그인 자격 증명"))
            }

            val googleIdToken = GoogleIdTokenCredential.createFrom(credential.data).idToken
            val firebaseCredential = GoogleAuthProvider.getCredential(googleIdToken, null)
            val authResult = auth.signInWithCredential(firebaseCredential).await()
            val user = authResult.user ?: return Result.failure(IllegalStateException("로그인 사용자 없음"))
            Result.success(user)
        } catch (e: GetCredentialException) {
            Result.failure(e)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            // Exception이 아니라 Throwable까지 받는다 — Play 서비스가 없거나 버전이 안 맞는 기기에서
            // Credential Manager 관련 클래스가 없어 NoClassDefFoundError처럼 Error 계열이 튀는 경우가
            // 있는데, Exception만 잡으면 그대로 앱이 죽는다(로그인 실패 메시지 대신 크래시로 보임).
            Result.failure(e)
        }
    }

    fun signOut() {
        auth.signOut()
    }
}
