package com.young.aircraft.gui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.young.aircraft.R
import com.young.aircraft.data.PlayerGameData
import com.young.aircraft.databinding.FragmentHistoryBinding
import com.young.aircraft.providers.DatabaseProvider
import com.young.aircraft.utils.HallOfHeroesNameUtils
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.util.Locale

class HistoryFragment : Fragment() {
    private var _binding: FragmentHistoryBinding? = null
    private val binding get() = _binding!!
    private val scoreFormatter = NumberFormat.getNumberInstance(Locale.US)

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
        binding.btnBack.setOnClickListener {
            requireActivity().finish()
        }
        binding.recyclerHistory.layoutManager = LinearLayoutManager(requireContext())
        loadHistory()
    }

    private fun loadHistory() {
        viewLifecycleOwner.lifecycleScope.launch {
            val records = DatabaseProvider.getDatabase(requireContext())
                .playerGameDataDao()
                .getAllByScoreDesc()

            updateSummary(records)

            if (records.isEmpty()) {
                showEmpty()
            } else {
                binding.recyclerHistory.visibility = View.VISIBLE
                binding.emptyState.visibility = View.GONE
                binding.recyclerHistory.adapter = HistoryAdapter(records.toMutableList()) { item ->
                    confirmDelete(item)
                }
            }
        }
    }

    private fun updateSummary(records: List<PlayerGameData>) {
        val topRecord = records.firstOrNull()
        binding.tvRecordCountChip.text = getString(
            R.string.history_summary_record_count,
            records.size
        )
        binding.tvBestScoreChip.text = if (topRecord == null) {
            getString(R.string.history_summary_best_score_empty)
        } else {
            getString(
                R.string.history_summary_best_score,
                scoreFormatter.format(topRecord.score)
            )
        }
        binding.tvSummaryOverview.text = if (topRecord == null) {
            getString(R.string.history_summary_empty_description)
        } else {
            getString(
                R.string.history_summary_with_top_pilot,
                HallOfHeroesNameUtils.getDisplayName(topRecord),
                topRecord.level
            )
        }
    }

    private fun confirmDelete(item: PlayerGameData) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(getString(R.string.history_delete_title))
            .setMessage(getString(R.string.history_delete_message))
            .setPositiveButton(getString(R.string.history_delete)) { _, _ ->
                deleteRecord(item)
            }
            .setNegativeButton(getString(R.string.history_cancel), null)
            .show()
    }

    private fun deleteRecord(item: PlayerGameData) {
        viewLifecycleOwner.lifecycleScope.launch {
            DatabaseProvider.getDatabase(requireContext())
                .playerGameDataDao()
                .delete(item)
            loadHistory()
        }
    }

    private fun showEmpty() {
        binding.recyclerHistory.visibility = View.GONE
        binding.emptyState.visibility = View.VISIBLE
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
