#!/bin/bash


RELEASE_VERSION=$2
DEFAULT_BRANCH=$1
MAVEN_ADDITIONAL_OPTIONS=$3

[ $# != 3 ] && echo "::error::prepare-next-development-version needs exactly 3 arguments." && exit 1
test -z "${RELEASE_VERSION}" && echo "::debug::Skipping Release because release-version is unset" && exit 0
test -z "${DEFAULT_BRANCH}" && echo "::error::Default branch needs to be passed" && exit 1

git fetch --no-tags
git checkout "${DEFAULT_BRANCH}"

[ "$(git rev-list -n1 "${RELEASE_VERSION}")" != "$(git rev-list -n1 "${DEFAULT_BRANCH}")" ] && echo "${RELEASE_VERSION} not pointing to tip of ${DEFAULT_BRANCH}" && exit 0

# Commit the release version change in the pom.xml
git add ./**pom.xml
git commit -am "release(v${RELEASE_VERSION})"

# Overwrite existing release tag that was created to trigger this function with release version in pom.xml
git tag -fa "${RELEASE_VERSION}" -m "release(v${RELEASE_VERSION})"
git push origin --tags -f

# shellcheck disable=SC2086 # don't use quotes because we do want argument splitting
mvn -B ${MAVEN_ADDITIONAL_OPTIONS} org.apache.maven.plugins:maven-release-plugin:update-versions -DgenerateBackupPoms=false

# Commit next version calculated by maven
# https://maven.apache.org/guides/getting-started/index.html#what-is-a-snapshot-version
git add ./**pom.xml
git commit -am "release(v${RELEASE_VERSION}): prepare for next development iteration"
git push
