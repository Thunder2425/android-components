/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.samples.browser.addons

import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.RatingBar
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import mozilla.components.feature.addons.AddOn
import org.mozilla.samples.browser.R
import org.mozilla.samples.browser.addons.PermissionsDialogFragment.PromptsStyling
import org.mozilla.samples.browser.ext.components

/**
 * Fragment use for managing add-ons.
 */
class AddOnsFragment : Fragment(), View.OnClickListener {
    private lateinit var recyclerView: RecyclerView
    private val scope = CoroutineScope(Dispatchers.IO)

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.fragment_add_ons, container, false)
    }

    override fun onViewCreated(rootView: View, savedInstanceState: Bundle?) {
        super.onViewCreated(rootView, savedInstanceState)
        bindRecyclerView(rootView)
    }

    override fun onStart() {
        super.onStart()
        findPreviousDialogFragment()?.let { dialog ->
            dialog.onPositiveButtonClicked = onPositiveButtonClicked
            dialog.onNegativeButtonClicked = onNegativeButtonClicked
        }
    }

    private fun bindRecyclerView(rootView: View) {
        recyclerView = rootView.findViewById(R.id.add_ons_list)
        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        scope.launch {
            val addOns = requireContext().components.addOnProvider.getAvailableAddOns()

            scope.launch(Dispatchers.Main) {
                val adapter = AddOnsAdapter(
                    this@AddOnsFragment,
                    addOns
                )
                recyclerView.adapter = adapter
            }
        }
    }

    /**
     * An adapter for displaying add-on items.
     */
    inner class AddOnsAdapter(
        private val clickListener: View.OnClickListener,
        private val addOns: List<AddOn>
    ) :
        RecyclerView.Adapter<AddOnViewHolder>() {
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AddOnViewHolder {
            val context = parent.context
            val inflater = LayoutInflater.from(context)
            val view = inflater.inflate(R.layout.add_ons_item, parent, false)
            val iconView = view.findViewById<ImageView>(R.id.add_on_icon)
            val titleView = view.findViewById<TextView>(R.id.add_on_name)
            val summaryView = view.findViewById<TextView>(R.id.add_on_description)
            val ratingView = view.findViewById<RatingBar>(R.id.rating)
            val userCountView = view.findViewById<TextView>(R.id.users_count)
            val addButton = view.findViewById<ImageView>(R.id.add_button)
            return AddOnViewHolder(
                view,
                iconView,
                titleView,
                summaryView,
                ratingView,
                userCountView,
                addButton
            )
        }

        override fun getItemCount() = addOns.size

        override fun onBindViewHolder(holder: AddOnViewHolder, position: Int) {
            val addOn = addOns[position]
            val context = holder.view.context
            //  For loading the icon we need https://github.com/mozilla-mobile/android-components/issues/4175
            addOn.rating?.let {
                val userCount = context.getString(R.string.add_on_user_rating_count)
                val ratingContentDescription =
                    context.getString(R.string.add_on_rating_content_description)
                holder.ratingView.contentDescription =
                    String.format(ratingContentDescription, it.average)
                holder.ratingView.rating = it.average
                holder.userCountView.text = String.format(userCount, getFormattedAmount(it.reviews))
            }

            holder.titleView.text = addOn.translatableName.translate()
            holder.summaryView.text = addOn.translatableSummary.translate()
            holder.view.tag = addOn
            holder.view.setOnClickListener(clickListener)
            holder.addButton.setOnClickListener(clickListener)

            scope.launch {
                val iconBitmap = context.components.addOnProvider.getAddOnIconBitmap(addOn)

                iconBitmap?.let {
                    MainScope().launch {
                        holder.iconView.setImageBitmap(it)
                    }
                }
            }
        }
    }

    /**
     * A view holder for displaying add-on items.
     */
    class AddOnViewHolder(
        val view: View,
        val iconView: ImageView,
        val titleView: TextView,
        val summaryView: TextView,
        val ratingView: RatingBar,
        val userCountView: TextView,
        val addButton: ImageView
    ) : RecyclerView.ViewHolder(view)

    override fun onClick(view: View) {
        val context = view.context
        when (view.id) {
            R.id.add_button -> {
                val addOn = (((view.parent) as View).tag as AddOn)
                showPermissionDialog(addOn)
            }
            R.id.add_on_item -> {
                val intent = Intent(context, InstalledAddOnDetailsActivity::class.java)
                intent.putExtra("add_on", view.tag as AddOn)
                context.startActivity(intent)
            }
            else -> {
            }
        }
    }

    private fun isAlreadyADialogCreated(): Boolean {
        return findPreviousDialogFragment() != null
    }

    private fun findPreviousDialogFragment(): PermissionsDialogFragment? {
        return fragmentManager?.findFragmentByTag(PERMISSIONS_DIALOG_FRAGMENT_TAG) as? PermissionsDialogFragment
    }

    private fun showPermissionDialog(addOn: AddOn) {

        val dialog = PermissionsDialogFragment.newInstance(
            addOnId = addOn.id,
            title = addOn.translatableName.translate(),
            permissions = addOn.translatePermissions(),
            promptsStyling = PromptsStyling(
                gravity = Gravity.BOTTOM,
                shouldWidthMatchParent = true
            ),
            onPositiveButtonClicked = onPositiveButtonClicked,
            onNegativeButtonClicked = onNegativeButtonClicked
        )

        if (!isAlreadyADialogCreated() && fragmentManager != null) {
            dialog.show(requireFragmentManager(), PERMISSIONS_DIALOG_FRAGMENT_TAG)
        }
    }

    private val onPositiveButtonClicked: ((String) -> Unit) = { addonId ->
        Toast.makeText(this.requireContext(), "Installing add-on $addonId", Toast.LENGTH_SHORT)
            .show()
    }

    private val onNegativeButtonClicked = {
        Toast.makeText(this.requireContext(), "Cancel button clicked", Toast.LENGTH_SHORT)
            .show()
    }
    companion object {
        private const val PERMISSIONS_DIALOG_FRAGMENT_TAG = "ADDONS_PERMISSIONS_DIALOG_FRAGMENT"
    }
}
