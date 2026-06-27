package android.kimyona.jammer.ui.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import android.kimyona.jammer.R
import android.kimyona.jammer.ui.adapter.QueueAdapter
import android.kimyona.jammer.ui.viewmodel.PlayerViewModel

/**
 * QueueFragment evoluído — funcional com drag-and-drop, remove e play.
 */
class QueueFragment : Fragment() {

    private val viewModel: PlayerViewModel by activityViewModels()
    private lateinit var adapter: QueueAdapter
    private lateinit var recyclerView: RecyclerView
    private lateinit var tvQueueStatus: TextView
    private lateinit var btnClearQueue: Button

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_queue, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        recyclerView = view.findViewById(R.id.recyclerQueue)
        tvQueueStatus = view.findViewById(R.id.tvQueueStatus)
        btnClearQueue = view.findViewById(R.id.btnClearQueue)

        setupRecyclerView()
        setupDragAndDrop()

        // Observa queue
        viewModel.queueTracks.observe(viewLifecycleOwner) { tracks ->
            adapter.submitList(tracks)
            if (tracks.isNullOrEmpty()) {
                tvQueueStatus.text = "Queue is empty"
                btnClearQueue.visibility = View.GONE
            } else {
                tvQueueStatus.text = "${tracks.size} tracks in queue"
                btnClearQueue.visibility = View.VISIBLE
            }
        }

        // Observa tamanho da queue
        viewModel.queueSize.observe(viewLifecycleOwner) { size ->
            if (size == 0) {
                tvQueueStatus.text = "Queue is empty"
                btnClearQueue.visibility = View.GONE
            }
        }

        btnClearQueue.setOnClickListener {
            viewModel.clearQueue()
            Toast.makeText(requireContext(), "Queue cleared", Toast.LENGTH_SHORT).show()
        }
    }

    private fun setupRecyclerView() {
        adapter = QueueAdapter(
            onItemClick = { track, index ->
                // Toca a partir desta posição
                val currentQueue = viewModel.queueTracks.value ?: return@QueueAdapter
                viewModel.playPlaylist(currentQueue, index)
            },
            onItemRemove = { index ->
                viewModel.removeFromQueue(index)
                Toast.makeText(requireContext(), "Removed from queue", Toast.LENGTH_SHORT).show()
            },
            onDragStart = { viewHolder ->
                itemTouchHelper.startDrag(viewHolder)
            }
        )

        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        recyclerView.adapter = adapter
    }

    private lateinit var itemTouchHelper: ItemTouchHelper

    private fun setupDragAndDrop() {
        val callback = object : ItemTouchHelper.SimpleCallback(
            ItemTouchHelper.UP or ItemTouchHelper.DOWN, 0
        ) {
            override fun onMove(
                recyclerView: RecyclerView,
                from: RecyclerView.ViewHolder,
                to: RecyclerView.ViewHolder
            ): Boolean {
                val fromPos = from.adapterPosition
                val toPos = to.adapterPosition
                if (fromPos == RecyclerView.NO_POSITION || toPos == RecyclerView.NO_POSITION) return false

                viewModel.moveQueueItem(fromPos, toPos)

                // Update local list for smooth UI
                val currentList = adapter.currentList.toMutableList()
                val item = currentList.removeAt(fromPos)
                currentList.add(toPos, item)
                adapter.submitList(currentList)

                return true
            }

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                // Swipe not used — long-press to remove
            }

            override fun isLongPressDragEnabled(): Boolean = false
        }

        itemTouchHelper = ItemTouchHelper(callback)
        itemTouchHelper.attachToRecyclerView(recyclerView)
    }
}
