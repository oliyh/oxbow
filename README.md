# oxbow

A Server Sent Events (SSE) client for Clojurescript based on js/fetch

[![Clojars Project](https://img.shields.io/clojars/v/oliyh/oxbow.svg)](https://clojars.org/oliyh/oxbow)

This library uses js/fetch to expose the full functionality of HTTP and processes the SSE data for you.

It's ready for use as a standalone functional Clojurescript library and also has re-frame bindings for integration
into a re-frame application.

## Rationale

js/EventSource is generally moribund and does not support sending headers.
Discussions generally reference js/fetch as a modern alternative but it does not natively process streams.

## Development

`cider-jack-in-cljs` and open the test page http://localhost:9500/figwheel-extra-main/auto-testing

## Build
[![CircleCI](https://circleci.com/gh/oliyh/oxbow.svg?style=svg)](https://circleci.com/gh/oliyh/oxbow)

## License

Copyright © 2020 oliyh

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
