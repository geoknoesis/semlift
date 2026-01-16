package io.semlift.rdf4j

import io.semlift.RdfDataset
import org.eclipse.rdf4j.model.Model

data class Rdf4jDataset(val model: Model) : RdfDataset

