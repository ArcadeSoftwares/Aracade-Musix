package com.arcadesoftware.musix.workers

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

class DeleteSharedPlaylistWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val docId = inputData.getString("docId") ?: return Result.failure()
        
        return try {
            FirebaseFirestore.getInstance()
                .collection("shared_playlists")
                .document(docId)
                .delete()
                .await()
            Result.success()
        } catch (e: Exception) {
            // If it fails (e.g. no internet), WorkManager will automatically retry it later
            // because we can return Result.retry() or it might just fail depending on constraints.
            // But we should return Result.retry() to make sure it gets deleted eventually.
            Result.retry()
        }
    }
}
