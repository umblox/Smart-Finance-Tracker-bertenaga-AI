package com.smartfinance.tracker.ui.chat

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.smartfinance.tracker.R
import com.smartfinance.tracker.databinding.FragmentChatBinding
import kotlinx.coroutines.launch

class ChatFragment : Fragment() {

    private var _binding: FragmentChatBinding? = null
    private val binding get() = _binding!!
    
    private lateinit var viewModel: ChatViewModel
    private lateinit var chatAdapter: ChatAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentChatBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        viewModel = ViewModelProvider(this)[ChatViewModel::class.java]

        val layoutManager = LinearLayoutManager(requireContext()).apply {
            stackFromEnd = true 
        }
        binding.rvChatHistory.layoutManager = layoutManager

        binding.btnSend.setOnClickListener {
            val message = binding.etMessage.text.toString().trim()
            if (message.isNotEmpty()) {
                viewModel.sendMessage(message)
                binding.etMessage.setText("")
            }
        }

        binding.btnClearChat.setOnClickListener {
            // 🔥 FIX: Dihapus paksaan temanya agar menjadi Pop-up cantik yang proporsional
            MaterialAlertDialogBuilder(requireContext()).apply {
                setTitle(getString(R.string.chat_clear_title))
                setMessage(getString(R.string.chat_clear_message))
                setPositiveButton(getString(R.string.chat_clear_confirm)) { _, _ ->
                    viewModel.clearChat()
                }
                setNegativeButton(getString(R.string.action_cancel), null)
                show()
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.uiState.collect { state ->
                chatAdapter = ChatAdapter(state.messages)
                binding.rvChatHistory.adapter = chatAdapter
                
                if (state.messages.isNotEmpty()) {
                    binding.rvChatHistory.scrollToPosition(state.messages.size - 1)
                }

                binding.btnSend.isEnabled = !state.isTyping
                binding.btnSend.alpha = if (state.isTyping) 0.5f else 1.0f
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
