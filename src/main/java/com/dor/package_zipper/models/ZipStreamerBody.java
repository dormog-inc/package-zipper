
package com.dor.package_zipper.models;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;

@AllArgsConstructor
@Getter
@Setter
@Data
@Builder
public class ZipStreamerBody {
    private List<ZipRemoteEntry> entries;
}
