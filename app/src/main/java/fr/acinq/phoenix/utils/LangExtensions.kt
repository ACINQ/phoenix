/*
 * Copyright 2020 ACINQ SAS
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package fr.acinq.phoenix.utils

import android.view.View
import androidx.fragment.app.Fragment
import androidx.navigation.NavController
import androidx.navigation.Navigation
import androidx.navigation.fragment.NavHostFragment
import org.slf4j.Logger
import org.slf4j.LoggerFactory

/**
 * Utility method rebinding any exceptions thrown by a method into another exception, using the origin exception as the root cause.
 * Helps with pattern matching.
 */
inline fun <T> tryWith(exception: Exception, action: () -> T): T = try {
  action.invoke()
} catch (t: Exception) {
  exception.initCause(t)
  throw exception
}
object LangExtensions {
  val log: Logger = LoggerFactory.getLogger(this::class.java)

  fun Fragment.findNavControllerSafe(): NavController? = try {
    NavHostFragment.findNavController(this)
  } catch (e: Exception) {
    log.warn("failed to find navigation controller in fragment=${this::class.qualifiedName.toString()}")
    null
  }

  fun View.findNavControllerSafe(): NavController? = try {
    Navigation.findNavController(this)
  } catch (e: Exception) {
    log.warn("failed to find navigation controller in view=${this::class.qualifiedName.toString()}")
    null
  }
}
