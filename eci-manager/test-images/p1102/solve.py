#!/usr/bin/env python3
"""P1102 A-B 数对：排序 + 双指针，与题解 C++ 逻辑等价。"""


def count_pairs(n: int, c: int, a: list[int]) -> int:
    a = sorted(a)
    ans = 0
    l = 0
    r = -1
    for i in range(n):
        while l < i and a[l] + c < a[i]:
            l += 1
        while r + 1 < i and a[r + 1] + c <= a[i]:
            r += 1
        ans += 1 + r - l
    return ans


def solve_from_text(text: str) -> str:
    lines = text.strip().splitlines()
    n_c = lines[0].split()
    n, c = int(n_c[0]), int(n_c[1])
    a = list(map(int, lines[1].split()))
    if len(a) != n:
        raise ValueError(f"expected {n} numbers, got {len(a)}")
    return f"{count_pairs(n, c, a)}\n"


if __name__ == "__main__":
    import sys

    sys.stdout.write(solve_from_text(sys.stdin.read()))
