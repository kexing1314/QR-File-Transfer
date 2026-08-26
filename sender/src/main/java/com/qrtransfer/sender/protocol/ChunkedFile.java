package com.qrtransfer.sender.protocol;

import java.util.List;

public record ChunkedFile(TransferManifest manifest, List<byte[]> chunks) {}
