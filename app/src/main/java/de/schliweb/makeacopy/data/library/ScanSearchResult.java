/*
 * Copyright 2025 Christian Kierdorf
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */
package de.schliweb.makeacopy.data.library;

/** Projection returned by local OCR full-text search queries. */
public class ScanSearchResult {
  public String scanId;
  public String title;
  public long createdAt;
  public String coverPath;
  public int pageIndex;
  public String snippet;
  public double rank;
}
