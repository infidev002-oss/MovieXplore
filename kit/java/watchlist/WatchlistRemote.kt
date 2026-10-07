package your.app.watchlist

import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener

object WatchlistRemote {
    private fun ref(uid: String) =
        FirebaseDatabase.getInstance().getReference("Users").child(uid).child("watchlist")

    fun add(uid: String, item: WatchlistItem) { ref(uid).child(item.uniqueId).setValue(item) }
    fun remove(uid: String, uniqueId: String) { ref(uid).child(uniqueId).removeValue() }
    fun getAll(uid: String, result: (List<WatchlistItem>) -> Unit) {
        ref(uid).get().addOnSuccessListener { s ->
            result(s.children.mapNotNull { it.getValue(WatchlistItem::class.java) })
        }.addOnFailureListener { result(emptyList()) }
    }
    fun listen(uid: String, onChange: (List<WatchlistItem>) -> Unit): ValueEventListener {
        val l = object : ValueEventListener {
            override fun onDataChange(s: DataSnapshot) =
                onChange(s.children.mapNotNull { it.getValue(WatchlistItem::class.java) })
            override fun onCancelled(e: DatabaseError) {}
        }
        ref(uid).addValueEventListener(l)
        return l
    }
    fun stopListening(uid: String, l: ValueEventListener) { ref(uid).removeEventListener(l) }
}
