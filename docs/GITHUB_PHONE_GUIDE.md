# GitHub + Android phone guide

These steps use the ZIP downloaded from this Arena session and GitHub's mobile app/browser. They do not require Claude Code or a computer. GitHub's mobile layout changes over time; if an option is not visible in the app, use Chrome at `github.com` and choose **Desktop site** from the browser menu.

## A. Download this source ZIP to your phone

1. Download `smart-transparent-mess-erp-phase1.zip` from the Arena file link in this conversation.
2. Open **Files by Google** or your phone's **My Files** app → **Downloads**.
3. Tap the ZIP and choose **Extract**. Keep the extracted folder intact; GitHub Actions needs the `.github/workflows`, `gradle/wrapper`, `app`, and `supabase` folders in the repository. Uploading the ZIP itself as a single file will not activate the workflow.

## B. Put the source in a GitHub repository

1. In the GitHub app or `github.com` browser, create a new repository. A **private** repository is a sensible default for a new project. Do not create a production release.
2. For bulk upload, use GitHub's website in Chrome (Desktop site if necessary) and its **Add file → Upload files** page. Choose the extracted project files and preserve their folder structure. Check that `.github/workflows/android-debug.yml` is in the repository root path; a workflow nested inside an extra ZIP or extra project folder will not run.
3. Commit the source to the repository's default branch. Never upload `local.properties`, private signing files, service-role keys, or database credentials.
4. If the Android file picker cannot select the extracted nested folders, GitHub's phone uploader may not be practical for that device/browser. Do not commit the source ZIP and assume Actions will build it; use a Git client that can push a directory tree or ask a trusted helper to upload the extracted source while preserving paths.

## C. Public Supabase configuration and optional overrides

1. The active project's public Android URL and publishable key are bundled in `config/supabase.public.properties`; no Variables are required for the connected debug build.
2. To use another project, optionally set `SUPABASE_URL` and `SUPABASE_PUBLISHABLE_KEY` under **Settings → Secrets and variables → Actions → Variables**. Use a **publishable/anon** key only. Blank values fall back to the bundled public config.
3. The workflow verifies generated `BuildConfig` before upload. Only very old artifacts without bundled configuration should display **Setup required**.

## D. Run the workflow and download the debug APK

1. Open the repository in GitHub → **Actions** → **Android debug APK**.
2. Tap **Run workflow** (or open the newest successful run after a commit). Wait for the green checkmark. If it fails, open the failed step/log; no APK is produced on failure.
3. On the run summary page, find **Artifacts** and tap `smart-transparent-mess-debug-apk-…` to download. The artifact is a ZIP containing `app-debug.apk`; GitHub keeps it for 14 days.
4. In **Files → Downloads**, extract the artifact ZIP, then tap `app-debug.apk` to install. Android may ask you to allow installs from the app you used to open the file. Allow this only if you trust the repository and workflow, install the APK, and turn the permission back off afterwards if you prefer. If Android reports a signature/update conflict with an older CI debug build, uninstall that old unconfigured build first; this removes its local app data.
5. This is a **debug-signed test APK**, not a Play Store release. Do not use it for production or sensitive member data until the backend, RLS, token logging, and app build have been reviewed.

If the GitHub app does not expose the artifact link, open the workflow run in Chrome. No automatic release is created by this project workflow.
