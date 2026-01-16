package io.semlift.jena

import io.semlift.RdfDataset
import org.apache.jena.query.Dataset

data class JenaDataset(val dataset: Dataset) : RdfDataset

