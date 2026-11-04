#!/usr/bin/env bash
# Builds a release folder with the API jar and the Angular files, ready to copy to the server:
#   ./deploy/build-release.sh            -> release/app.jar, release/browser/
# Runs the backend unit tests and the frontend tests first. Needs JDK 21 and Node.
set -euo pipefail
cd "$(dirname "$0")/.."

(cd backend && ./mvnw -q -Djacoco.skip=true clean package)
(cd frontend && npm ci && npx ng test --watch=false && npx ng build)

rm -rf release && mkdir -p release
cp backend/target/tshirt-shop-backend-*.jar release/app.jar
cp -r frontend/dist/tshirt-shop/browser release/browser
echo "Release ready in ./release (app.jar and browser/)"
