/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package mozilla.components.browser.session.engine

import android.graphics.Bitmap
import android.os.Environment
import androidx.core.net.toUri
import mozilla.components.browser.session.Session
import mozilla.components.browser.session.engine.request.LoadRequestMetadata
import mozilla.components.browser.session.engine.request.LoadRequestOption
import mozilla.components.browser.session.ext.syncDispatch
import mozilla.components.browser.state.action.ContentAction
import mozilla.components.browser.state.action.TrackingProtectionAction
import mozilla.components.browser.state.state.content.DownloadState
import mozilla.components.browser.state.store.BrowserStore
import mozilla.components.concept.engine.EngineSession
import mozilla.components.concept.engine.HitResult
import mozilla.components.concept.engine.content.blocking.Tracker
import mozilla.components.concept.engine.manifest.WebAppManifest
import mozilla.components.concept.engine.media.Media
import mozilla.components.concept.engine.media.RecordingDevice
import mozilla.components.concept.engine.permission.PermissionRequest
import mozilla.components.concept.engine.prompt.PromptRequest
import mozilla.components.concept.engine.window.WindowRequest
import mozilla.components.support.base.observer.Consumable

/**
 * [EngineSession.Observer] implementation responsible to update the state of a [Session] from the events coming out of
 * an [EngineSession].
 */
@Suppress("TooManyFunctions", "LargeClass")
internal class EngineObserver(
    private val session: Session,
    private val store: BrowserStore? = null
) : EngineSession.Observer {

    override fun onLocationChange(url: String) {
        if (session.url != url) {
            session.title = ""
        }

        if (!isHostEquals(session.url, url)) {
            session.icon = null
        }

        session.url = url

        // Meh, GeckoView doesn't notify us about recording devices no longer used when navigating away. As a
        // workaround we clear them here. But that's not perfect...
        // https://bugzilla.mozilla.org/show_bug.cgi?id=1554778
        session.recordingDevices = listOf()

        session.contentPermissionRequest.consume {
            it.reject()
            true
        }

        session.webAppManifest = null
    }

    private fun isHostEquals(sessionUrl: String, newUrl: String): Boolean {
        val sessionUri = sessionUrl.toUri()
        val newUri = newUrl.toUri()

        return sessionUri.scheme == newUri.scheme && sessionUri.host == newUri.host
    }

    override fun onLoadRequest(
        url: String,
        triggeredByRedirect: Boolean,
        triggeredByWebContent: Boolean,
        shouldLoadUri: (Boolean) -> Unit
    ) {
        if (triggeredByRedirect || triggeredByWebContent) {
            session.searchTerms = ""
        }

        session.loadRequestMetadata = Consumable.from(LoadRequestMetadata(
            url,
            arrayOf(
                if (triggeredByRedirect) LoadRequestOption.REDIRECT else LoadRequestOption.NONE,
                if (triggeredByWebContent) LoadRequestOption.WEB_CONTENT else LoadRequestOption.NONE
            ),
            shouldLoadUri
        ))
    }

    override fun onTitleChange(title: String) {
        session.title = title
    }

    override fun onProgress(progress: Int) {
        session.progress = progress
    }

    override fun onLoadingStateChange(loading: Boolean) {
        session.loading = loading
        if (loading) {
            session.findResults = emptyList()
            session.trackersBlocked = emptyList()
            session.trackersLoaded = emptyList()
        }
    }

    override fun onNavigationStateChange(canGoBack: Boolean?, canGoForward: Boolean?) {
        canGoBack?.let { session.canGoBack = canGoBack }
        canGoForward?.let { session.canGoForward = canGoForward }
    }

    override fun onSecurityChange(secure: Boolean, host: String?, issuer: String?) {
        session.securityInfo = Session.SecurityInfo(secure, host
                ?: "", issuer ?: "")
    }

    override fun onTrackerBlocked(tracker: Tracker) {
        session.trackersBlocked += tracker
    }

    override fun onTrackerLoaded(tracker: Tracker) {
        session.trackersLoaded += tracker
    }

    override fun onExcludedOnTrackingProtectionChange(excluded: Boolean) {
        store?.syncDispatch(TrackingProtectionAction.ToggleExclusionListAction(session.id, excluded))
    }

    override fun onTrackerBlockingEnabledChange(enabled: Boolean) {
        session.trackerBlockingEnabled = enabled
    }

    override fun onLongPress(hitResult: HitResult) {
        session.hitResult = Consumable.from(hitResult)
    }

    override fun onFind(text: String) {
        session.findResults = emptyList()
    }

    override fun onFindResult(activeMatchOrdinal: Int, numberOfMatches: Int, isDoneCounting: Boolean) {
        session.findResults += Session.FindResult(activeMatchOrdinal, numberOfMatches, isDoneCounting)
    }

    override fun onExternalResource(
        url: String,
        fileName: String?,
        contentLength: Long?,
        contentType: String?,
        cookie: String?,
        userAgent: String?
    ) {
        val download = DownloadState(
            url,
            fileName,
            contentType,
            contentLength,
            userAgent,
            Environment.DIRECTORY_DOWNLOADS
        )

        store?.dispatch(ContentAction.UpdateDownloadAction(
            session.id,
            download
        ))
    }

    override fun onDesktopModeChange(enabled: Boolean) {
        session.desktopMode = enabled
    }

    override fun onFullScreenChange(enabled: Boolean) {
        session.fullScreenMode = enabled
    }

    override fun onThumbnailChange(bitmap: Bitmap?) {
        session.thumbnail = bitmap
    }

    override fun onContentPermissionRequest(permissionRequest: PermissionRequest) {
        session.contentPermissionRequest = Consumable.from(permissionRequest)
    }

    override fun onCancelContentPermissionRequest(permissionRequest: PermissionRequest) {
        session.contentPermissionRequest = Consumable.empty()
    }

    override fun onAppPermissionRequest(permissionRequest: PermissionRequest) {
        session.appPermissionRequest = Consumable.from(permissionRequest)
    }

    override fun onPromptRequest(promptRequest: PromptRequest) {
        store?.dispatch(ContentAction.UpdatePromptRequestAction(
            session.id,
            promptRequest
        ))
    }

    override fun onWindowRequest(windowRequest: WindowRequest) {
        store?.dispatch(
            ContentAction.UpdateWindowRequestAction(
                session.id,
                windowRequest
            )
        )
    }

    override fun onMediaAdded(media: Media) {
        session.media = session.media.toMutableList().also {
            it.add(media)
        }
    }

    override fun onMediaRemoved(media: Media) {
        session.media = session.media.toMutableList().also {
            it.remove(media)
        }
        media.unregisterObservers()
    }

    override fun onWebAppManifestLoaded(manifest: WebAppManifest) {
        session.webAppManifest = manifest
    }

    override fun onCrash() {
        session.crashed = true
    }

    override fun onRecordingStateChanged(devices: List<RecordingDevice>) {
        session.recordingDevices = devices
    }
}
