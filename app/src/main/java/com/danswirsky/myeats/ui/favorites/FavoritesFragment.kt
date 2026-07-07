package com.danswirsky.myeats.ui.favorites

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.danswirsky.myeats.R
import com.danswirsky.myeats.databinding.FragmentFavoritesBinding
import com.danswirsky.myeats.ui.feed.RecipeAdapter

class FavoritesFragment : Fragment() {

    private var _binding: FragmentFavoritesBinding? = null
    private val binding get() = _binding!!
    private val viewModel: FavoritesViewModel by viewModels()
    private lateinit var adapter: RecipeAdapter

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentFavoritesBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        adapter = RecipeAdapter(
            onClick = { recipe ->
                findNavController().navigate(
                    R.id.action_favorites_to_details,
                    bundleOf("recipeId" to recipe.id),
                )
            },
            onSelectionChanged = { updateSelectionUi() },
        )
        // Share the ViewModel's set so selection survives rotation
        adapter.selectedIds = viewModel.selectedIds
        if (viewModel.selectionMode) adapter.setSelectionMode(true)

        binding.favoritesList.layoutManager = LinearLayoutManager(requireContext())
        binding.favoritesList.adapter = adapter

        binding.favoritesEditButton.setOnClickListener {
            setSelectionMode(!viewModel.selectionMode)
        }
        binding.removeSelectedButton.setOnClickListener {
            viewModel.removeSelected { count ->
                if (_binding == null) return@removeSelected
                Toast.makeText(
                    requireContext(),
                    getString(R.string.favorites_removed_format, count),
                    Toast.LENGTH_SHORT,
                ).show()
                setSelectionMode(false)
            }
        }

        viewModel.favorites.observe(viewLifecycleOwner) { recipes ->
            adapter.submitList(recipes)
            binding.favoritesEmpty.visibility = if (recipes.isEmpty()) View.VISIBLE else View.GONE
            binding.favoritesEditButton.visibility = if (recipes.isEmpty()) View.GONE else View.VISIBLE
            if (recipes.isEmpty() && viewModel.selectionMode) setSelectionMode(false)
        }

        updateSelectionUi()
    }

    private fun setSelectionMode(enabled: Boolean) {
        viewModel.selectionMode = enabled
        adapter.setSelectionMode(enabled)
        updateSelectionUi()
    }

    private fun updateSelectionUi() {
        if (_binding == null) return
        val selecting = viewModel.selectionMode
        binding.favoritesEditButton.text =
            getString(if (selecting) R.string.action_cancel else R.string.action_edit)
        binding.removeSelectedButton.visibility = if (selecting) View.VISIBLE else View.GONE
        val count = viewModel.selectedIds.size
        binding.removeSelectedButton.isEnabled = count > 0
        binding.removeSelectedButton.text = getString(R.string.remove_selected_format, count)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
