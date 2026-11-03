# ShopSmart ZA

Native Android starter project for a South African shopping-list and price-comparison app.

## Current starter features
- Native Android app built with Kotlin and Jetpack Compose.
- Shopping-list screen with a sample list.
- Estimated total displayed in South African Rand (R).
- GitHub Actions workflow to build a debug APK on pushes to `main` or manually.

## Planned next features
- Editable products, quantities and prices.
- Offline-first local database (Room).
- Save completed shopping transactions and price history.
- Product catalogue and repeat-purchase suggestions.
- Promotional pamphlet image/PDF import and OCR with user verification.
- Compare captured deals against shopping-list products.
- Optional online sync when a backend is configured.

## Build APK
The GitHub Actions workflow `.github/workflows/android.yml` builds `app-debug.apk`.
After a successful run, open the workflow run in GitHub Actions and download the `ShopSmart-ZA-debug-APK` artifact.

This is an initial starter build, not the finished feature set. Prices shown are estimates and should be checked against till prices.
