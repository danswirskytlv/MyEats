package com.danswirsky.myeats.ui.user

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.bumptech.glide.Glide
import com.danswirsky.myeats.R
import com.danswirsky.myeats.databinding.FragmentUserRecipesBinding
import com.danswirsky.myeats.ui.feed.RecipeAdapter

/** Shows every recipe uploaded by the cook that was tapped. */
class UserRecipesFragment : Fragment() {

    private var _binding: FragmentUserRecipesBinding? = null
    private val binding get() = _binding!!
    private val viewModel: UserRecipesViewModel by viewModels()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentUserRecipesBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val ownerUid = arguments?.getString("ownerUid") ?: return
        val ownerName = arguments?.getString("ownerName") ?: ""
        viewModel.start(ownerUid)

        binding.userHeader.text = ownerName
        binding.userBack.setOnClickListener { findNavController().popBackStack() }

        val adapter = RecipeAdapter(onClick = { recipe ->
            findNavController().navigate(
                R.id.action_user_to_details,
                bundleOf("recipeId" to recipe.id),
            )
        })
        binding.userRecipesList.layoutManager = LinearLayoutManager(requireContext())
        binding.userRecipesList.adapter = adapter

        viewModel.recipes.observe(viewLifecycleOwner) { recipes ->
            adapter.submitList(recipes)
            binding.userRecipesEmpty.visibility = if (recipes.isEmpty()) View.VISIBLE else View.GONE

            binding.cookStatRecipes.text = recipes.size.toString()
            val ratingSum = recipes.sumOf { it.ratingSum }
            val ratingCount = recipes.sumOf { it.ratingCount }
            binding.cookStatRating.text = if (ratingCount > 0) {
                String.format(java.util.Locale.US, "%.1f", ratingSum.toDouble() / ratingCount)
            } else {
                getString(R.string.stat_no_rating)
            }
        }

        viewModel.cook.observe(viewLifecycleOwner) { cook ->
            if (cook == null) return@observe
            binding.cookBio.text = cook.bio
            if (cook.photoUrl.isNotEmpty()) {
                Glide.with(this).load(cook.photoUrl).circleCrop().into(binding.cookAvatar)
                binding.cookAvatar.setPadding(0, 0, 0, 0)
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
