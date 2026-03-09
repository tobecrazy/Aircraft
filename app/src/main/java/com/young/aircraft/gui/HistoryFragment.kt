package com.young.aircraft.gui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import com.young.aircraft.R
import com.young.aircraft.data.AppDatabase
import com.young.aircraft.data.PlayerGameData
import com.young.aircraft.databinding.FragmentHistoryBinding
import kotlinx.coroutines.launch

class HistoryFragment : Fragment() {
    private var _binding: FragmentHistoryBinding? = null
    private val binding get() = _binding!!
    private var adapter: HistoryAdapter? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHistoryBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.toolbar.setNavigationOnClickListener {
            requireActivity().finish()
        }
        binding.recyclerHistory.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerHistory.addItemDecoration(
            DividerItemDecoration(requireContext(), DividerItemDecoration.VERTICAL)
        )
        loadHistory()
    }

    private fun loadHistory() {
        viewLifecycleOwner.lifecycleScope.launch {
            val records = AppDatabase.getInstance(requireContext())
                .playerGameDataDao()
                .getAllByScoreDesc()

            if (records.isEmpty()) {
                showEmpty()
            } else {
                binding.recyclerHistory.visibility = View.VISIBLE
                binding.textEmpty.visibility = View.GONE
                adapter = HistoryAdapter(records.toMutableList()) { item, position ->
                    confirmDelete(item, position)
                }
                binding.recyclerHistory.adapter = adapter
            }
        }
    }

    private fun confirmDelete(item: PlayerGameData, position: Int) {
        AlertDialog.Builder(requireContext())
            .setTitle(getString(R.string.history_delete_title))
            .setMessage(getString(R.string.history_delete_message))
            .setPositiveButton(getString(R.string.history_delete)) { _, _ ->
                deleteRecord(item, position)
            }
            .setNegativeButton(getString(R.string.history_cancel), null)
            .show()
    }

    private fun deleteRecord(item: PlayerGameData, position: Int) {
        viewLifecycleOwner.lifecycleScope.launch {
            AppDatabase.getInstance(requireContext())
                .playerGameDataDao()
                .delete(item)
            adapter?.removeAt(position)
            if (adapter?.itemCount == 0) {
                showEmpty()
            }
        }
    }

    private fun showEmpty() {
        binding.recyclerHistory.visibility = View.GONE
        binding.textEmpty.visibility = View.VISIBLE
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
