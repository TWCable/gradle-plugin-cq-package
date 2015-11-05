/*
 * Copyright 2014-2015 Time Warner Cable, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.twcable.gradle.cqpackage

import javax.annotation.Nullable

/**
 * A simple "union class" that allows for passing back either a success (in "value") or a failure (in "error").
 * <p/>
 * While it's possible for "value" to be null, if there's a failure then "error" is guaranteed to not be null.
 * <p/>
 * Use {@link SuccessOrFailure#succeeded()} or {@link SuccessOrFailure#failed()} to know if the call was successful.
 */
final class SuccessOrFailure<T> {
    @Nullable
    final T value

    @Nullable
    final Status error


    private SuccessOrFailure(T value, Status error) {
        this.value = value
        this.error = error
    }


    boolean succeeded() {
        return error == null
    }


    boolean failed() {
        return error != null
    }


    static <T> SuccessOrFailure<T> success(T value) {
        return new SuccessOrFailure<T>(value, null)
    }


    static <T> SuccessOrFailure<T> failure(Status error) {
        if (error == null) throw new IllegalArgumentException("error == null")
        return new SuccessOrFailure<T>(null, error)
    }

}
