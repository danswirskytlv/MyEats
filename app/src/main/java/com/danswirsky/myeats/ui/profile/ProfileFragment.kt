package com.danswirsky.myeats.ui.profile

import android.content.Intent
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
import com.danswirsky.myeats.databinding.FragmentProfileBinding
import com.danswirsky.myeats.ui.auth.AuthActivity
import com.danswirsky.myeats.ui.feed.RecipeAdapter
import java.util.Locale

class ProfileFragment : Fragment() {

    private var _binding: FragmentProfileBinding? = null
    private val binding get() = _binding!!
    private val viewModel: ProfileViewModel by viewModels()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentProfileBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.profileEmail.text = viewModel.email

        val adapter = RecipeAdapter(onClick = { recipe ->
            findNavController().navigate(
                R.id.action_profile_to_details,
                bundleOf("recipeId" to recipe.id),
            )
        })
        binding.profileList.layoutManager = LinearLayoutManager(requireContext())
        binding.profileList.adapter = adapter

        viewModel.user.observe(viewLifecycleOwner) { profile ->
            binding.profileName.text = profile?.name ?: ""
            val bio = profile?.bio.orEmpty()
            binding.profileBio.text = bio
            binding.profileBio.visibility = if (bio.isEmpty()) View.GONE else View.VISIBLE
            if (!profile?.photoUrl.isNullOrEmpty()) {
                Glide.with(this).load(profile?.photoUrl).circleCrop().into(binding.profileAvatar)
                binding.profileAvatar.setPadding(0, 0, 0, 0)
            }
        }

        binding.editProfileButton.setOnClickListener {
            findNavController().navigate(R.id.action_profile_to_edit_profile)
        }
        viewModel.stats.observe(viewLifecycleOwner) { stats ->
            binding.statRecipes.text = stats.recipeCount.toString()
            binding.statRating.text = if (stats.avgRating > 0) {
                String.format(Locale.US, "%.1f", stats.avgRating)
            } else {
                getString(R.string.stat_no_rating)
            }
        }
        viewModel.myRecipes.observe(viewLifecycleOwner) { recipes ->
            adapter.submitList(recipes)
            binding.profileEmpty.visibility = if (recipes.isEmpty()) View.VISIBLE else View.GONE
        }

        binding.logoutButton.setOnClickListener {
            viewModel.logout()
            startActivity(Intent(requireContext(), AuthActivity::class.java))
            requireActivity().finish()
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.refresh()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
