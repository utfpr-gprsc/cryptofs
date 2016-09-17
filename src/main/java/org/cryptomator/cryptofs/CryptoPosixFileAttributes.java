/*******************************************************************************
 * Copyright (c) 2016 Sebastian Stenzel and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the accompanying LICENSE.txt.
 *
 * Contributors:
 *     Sebastian Stenzel - initial API and implementation
 *******************************************************************************/
package org.cryptomator.cryptofs;

import java.nio.file.Path;
import java.nio.file.attribute.PosixFileAttributes;

import org.cryptomator.cryptolib.api.Cryptor;

class CryptoPosixFileAttributes extends CryptoBasicFileAttributes implements DelegatingPosixFileAttributes {

	private final PosixFileAttributes delegate;

	public CryptoPosixFileAttributes(PosixFileAttributes delegate, Path ciphertextPath, Cryptor cryptor) {
		super(delegate, ciphertextPath, cryptor);
		this.delegate = delegate;
	}

	@Override
	public PosixFileAttributes getDelegate() {
		return delegate;
	}

}
