package com.young.aircraft.gui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.young.aircraft.R
import com.young.aircraft.data.PlayerGameData
import com.young.aircraft.databinding.FragmentHistoryBinding
import com.young.aircraft.providers.DatabaseProvider
import com.young.aircraft.viewmodel.HistoryUiState
import com.young.aircraft.viewmodel.HistoryViewModel
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.util.Locale

class HistoryFragment : Fragment() {
    private var _binding: FragmentHistoryBinding? = null
    private val binding get() = _binding!!
    private val scoreFormatter = NumberFormat.getNumberInstance(Locale.US)

    private lateinit var viewModel: HistoryViewModel

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

        val dao = DatabaseProvider.getDatabase(requireContext()).playerGameDataDao()
        viewModel = ViewModelProvider(this, HistoryViewModel.Factory(dao))[HistoryViewModel::class.java]

        binding.btnBack.setOnClickListener { requireActivity().finish() }
        binding.recyclerHistory.layoutManager = LinearLayoutManager(requireContext())

        observeState()
    }

    private fun observeState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    renderState(state)
                }
            }
        }
    }

    private fun renderState(state: HistoryUiState) {
        binding.tvRecordCountChip.text = getString(
            R.string.history_summary_record_count,
            state.recordCount
        )
        binding.tvBestScoreChip.text = if (state.bestScore == null) {
            getString(R.string.history_summary_best_score_empty)
        } else {
            getString(
                R.string.history_summary_best_score,
                scoreFormatter.format(state.bestScore)
            )
        }
        binding.tvSummaryOverview.text = if (state.topPilotName == null) {
            getString(R.string.history_summary_empty_description)
        } else {
            getString(
                R.string.history_summary_with_top_pilot,
                state.topPilotName,
                state.topPilotLevel
            )
        }

        if (state.records.isEmpty() && !state.isLoading) {
            binding.recyclerHistory.visibility = View.GONE
            binding.emptyState.visibility = View.VISIBLE
        } else {
            binding.recyclerHistory.visibility = View.VISIBLE
            binding.emptyState.visibility = View.GONE
            binding.recyclerHistory.adapter = HistoryAdapter(state.records.toMutableList()) { item ->
                confirmDelete(item)
            }
        }
    }

    private fun confirmDelete(item: PlayerGameData) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(getString(R.string.history_delete_title))
            .setMessage(getString(R.string.history_delete_message))
            .setPositiveButton(getString(R.string.history_delete)) { _, _ ->
                viewModel.deleteRecord(item)
            }
            .setNegativeButton(getString(R.string.history_cancel), null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
