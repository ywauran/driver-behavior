package com.andi.driver.behavior

import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.database.*
import com.andi.driver.behavior.HistoryAdapter

class HistoryActivity : AppCompatActivity(), HistoryAdapter.OnItemClickListener {

    private lateinit var database: DatabaseReference
    private lateinit var historyAdapter: HistoryAdapter
    private lateinit var currentUser: FirebaseUser

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_history)

        try {
            // Initialize currentUser
            currentUser = FirebaseAuth.getInstance().currentUser!!

            // Initialize Firebase Database
            database = FirebaseDatabase.getInstance().reference.child("history").child(currentUser.uid)

            // Initialize RecyclerView and Adapter
            val historyRecyclerView = findViewById<RecyclerView>(R.id.historyRecyclerView)
            historyAdapter = HistoryAdapter(this)
            historyRecyclerView.apply {
                layoutManager = LinearLayoutManager(this@HistoryActivity)
                adapter = historyAdapter
            }

            // Apply system insets
            ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
                val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
                v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
                insets
            }

            // Listen for data changes from Firebase
            database.addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    try {
                        val historyList = mutableListOf<DetectionData>()
                        for (dataSnapshot in snapshot.children) {
                            val detectionData = dataSnapshot.getValue(DetectionData::class.java)
                            detectionData?.let {
                                // Set the ID from the key
                                it.id = dataSnapshot.key ?: ""
                                historyList.add(it)
                            }
                        }
                        // Update RecyclerView with the new data
                        historyAdapter.submitList(historyList)
                    } catch (e: Exception) {
                        Log.e("HistoryActivity", "Error parsing data: ${e.message}")
                        Toast.makeText(this@HistoryActivity, "Error parsing data: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }

                override fun onCancelled(error: DatabaseError) {
                    // Handle error
                    Log.e("HistoryActivity", "Database error: ${error.message}")
                    Toast.makeText(this@HistoryActivity, "Database error: ${error.message}", Toast.LENGTH_SHORT).show()
                }
            })
        } catch (e: Exception) {
            Log.e("HistoryActivity", "Initialization error: ${e.message}")
            Toast.makeText(this, "Initialization error: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onItemClick(position: Int) {
        try {
            val currentItem = historyAdapter.currentList[position]
            val itemId = currentItem.id
            deleteHistoryItem(itemId, currentUser.uid)
        } catch (e: Exception) {
            Log.e("HistoryActivity", "Error handling item click: ${e.message}")
            Toast.makeText(this, "Error handling item click: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun deleteHistoryItem(itemId: String, userId: String) {
        try {
            // Reference to the specific item to be deleted
            val itemRef = database.child(itemId)

            // Remove the item from the database
            itemRef.removeValue()
                .addOnSuccessListener {
                    // Handle successful deletion
                    Toast.makeText(this, "Data berhasil dihapus.", Toast.LENGTH_SHORT).show()
                }
                .addOnFailureListener { e ->
                    // Handle any errors that may occur
                    Log.e("HistoryActivity", "Kesalahan menghapus data: ${e.message}")
                    Toast.makeText(this, "Kesalahan menghapus item: ${e.message}", Toast.LENGTH_SHORT).show()
                }
        } catch (e: Exception) {
            Log.e("HistoryActivity", "Error deleting item: ${e.message}")
            Toast.makeText(this, "Kesalahan menghapus data: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
}
