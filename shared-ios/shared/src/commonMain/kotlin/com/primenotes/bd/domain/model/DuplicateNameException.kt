package com.primenotes.bd.domain.model

/**
 * Raised when a folder or tag would be created with a name that already exists.
 *
 * Names are unique per kind, ignoring case, so "Work" and "work" are the same
 * folder. This extends [IllegalArgumentException] so existing callers that already
 * treat a rejected name as invalid input keep working unchanged.
 */
class DuplicateNameException(val subject: String) :
    IllegalArgumentException("A $subject with this name already exists")
