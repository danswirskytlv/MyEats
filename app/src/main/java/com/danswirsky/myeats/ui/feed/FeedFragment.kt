package com.danswirsky.myeats.ui.feed

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
import com.danswirsky.myeats.data.repository.FeedSort
import com.danswirsky.myeats.databinding.FragmentFeedBinding
import com.danswirsky.myeats.util.dismissKeyboardOnTapOutside
import com.danswirsky.myeats.util.hideKeyboard
import com.google.android.material.chip.Chip

class FeedFragment : Fragment() {

    private var _binding: FragmentFeedBinding? = null
    private val binding get() = _binding!!
    private val viewModel: FeedViewModel by viewModels()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentFeedBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Tapping outside the search field closes the keyboard
        dismissKeyboardOnTapOutside(binding.root)

        binding.feedGreeting.setText(
            when (java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)) {
                in 5..11 -> R.string.greeting_morning
                in 12..17 -> R.string.greeting_afternoon
                else -> R.string.greeting_evening
            }
        )

        val adapter = RecipeAdapter(onClick = { recipe ->
            findNavController().navigate(
                R.id.action_feed_to_details,
                bundleOf("recipeId" to recipe.id),
            )
        })
        val layoutManager = LinearLayoutManager(requireContext())
        binding.recipeList.layoutManager = layoutManager
        binding.recipeList.adapter = adapter

        // Infinite scroll: fetch the next page when nearing the bottom
        binding.recipeList.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                // Scrolling the list also dismisses the keyboard
                if (dy != 0 && binding.searchInput.hasFocus()) hideKeyboard()
                if (dy <= 0) return
                if (layoutManager.findLastVisibleItemPosition() >= adapter.itemCount - 5) {
                    viewModel.loadMore()
                }
            }
        })
        viewModel.loadingMore.observe(viewLifecycleOwner) { loading ->
            binding.feedLoadingMore.visibility = if (loading) View.VISIBLE else View.GONE
        }

        setupSearch()
        setupCategoryChips()
        setupSortChips()

        viewModel.recipes.observe(viewLifecycleOwner) { recipes ->
            adapter.submitList(recipes)
            binding.emptyState.visibility = if (recipes.isEmpty()) View.VISIBLE else View.GONE
        }
    }

    private fun setupSearch() {
        binding.searchInput.doOnTextChanged { text, _, _, _ ->
            viewModel.setQuery(text?.toString().orEmpty())
        }
    }

    private fun setupCategoryChips() {
        val categories =
            listOf(FeedViewModel.CATEGORY_ALL) + resources.getStringArray(R.array.recipe_categories)

        categories.forEach { name ->
            val chip = Chip(requireContext()).apply {
                setTextAppearanceResource(R.style.TextAppearance_MyEats_Chip)
                text = name
                isCheckable = true
                // Restore the ViewModel's selection after rotation
                isChecked = name == viewModel.currentCategory()
            }
            binding.categoryChips.addView(chip)
        }

        binding.categoryChips.setOnCheckedStateChangeListener { group, checkedIds ->
            val checked = checkedIds.firstOrNull()
                ?.let { id -> group.findViewById<Chip>(id)?.text?.toString() }
            viewModel.setCategory(checked ?: FeedViewModel.CATEGORY_ALL)
        }
    }

    private fun setupSortChips() {
        // Restore selection after rotation
        binding.sortChips.check(
            when (viewModel.currentSort()) {
                FeedSort.NEWEST -> R.id.sort_newest
                FeedSort.TOP_RATED -> R.id.sort_top
            }
        )
        binding.sortChips.setOnCheckedStateChangeListener { _, checkedIds ->
            viewModel.setSort(
                if (checkedIds.firstOrNull() == R.id.sort_top) FeedSort.TOP_RATED
                else FeedSort.NEWEST
            )
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
