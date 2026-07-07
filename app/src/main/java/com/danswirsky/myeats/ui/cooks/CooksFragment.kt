package com.danswirsky.myeats.ui.cooks

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.os.bundleOf
import androidx.core.widget.doOnTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.danswirsky.myeats.R
import com.danswirsky.myeats.databinding.FragmentCooksBinding
import com.danswirsky.myeats.util.dismissKeyboardOnTapOutside
import com.danswirsky.myeats.util.hideKeyboard

/** Search screen for finding other cooks and opening their profile page. */
class CooksFragment : Fragment() {

    private var _binding: FragmentCooksBinding? = null
    private val binding get() = _binding!!
    private val viewModel: CooksViewModel by viewModels()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentCooksBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val adapter = CookAdapter(onClick = { user ->
            findNavController().navigate(
                R.id.action_cooks_to_user,
                bundleOf("ownerUid" to user.uid, "ownerName" to user.name),
            )
        })
        binding.cooksList.layoutManager = LinearLayoutManager(requireContext())
        binding.cooksList.adapter = adapter

        // Keyboard dismisses on tap outside or when scrolling the list
        dismissKeyboardOnTapOutside(binding.root)
        binding.cooksList.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                if (dy != 0 && binding.cooksSearchInput.hasFocus()) hideKeyboard()
            }
        })

        binding.cooksSearchInput.doOnTextChanged { text, _, _, _ ->
            viewModel.setQuery(text?.toString().orEmpty())
        }

        viewModel.cooks.observe(viewLifecycleOwner) { cooks ->
            adapter.submitList(cooks)
            binding.cooksEmpty.visibility = if (cooks.isEmpty()) View.VISIBLE else View.GONE
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
